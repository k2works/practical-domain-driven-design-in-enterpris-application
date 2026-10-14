// session の警告（Bolt 32b、ACC-06、WCAG 2.2.1、UI 設計の共通部品「session の警告」）。
// - 残り時間はサーバーが画面に出した秒数（無操作の期限、8 時間の上限までの残り）から、ブラウザの時計で数える。残りを問い合わせる
//   定期の request は送らない（問い合わせ自体が無操作の期限を延ばしてしまう）
// - 無操作の期限は「最後の request の時刻 + 無操作の秒数」。数える request は画面の読み込みと延長だけ（入力欄への入力、書類の
//   ダウンロードなど画面を移らない request は数えない。ブラウザの期限はサーバーより早く来ることがあり、安全側に倒れる）。最後の
//   request の時刻は localStorage に置いてタブの間でそろえる（置くのはミリ秒の時刻だけ。業務データは置かない）
// - 8 時間の期限は「読み込みの時刻 + 残りの秒数」。読み込みの時間だけブラウザの期限が遅れないよう、どちらの期限も 10 秒早める
// - 早いほうの期限の 5 分前に警告を開く。無操作の警告は「ログインを延長する」にフォーカスを移し、8 時間の警告は延長を出さない
// - 延長は htmx でなく fetch で送る（302 を opaqueredirect として受け、失効を見分けるため）。204 なら閉じ、302 なら期限の到来、
//   それ以外（403・500・通信の失敗）は「延長できませんでした」を role="alert" で示して延長のボタンを残す
// - 期限を過ぎたら、モーダルを閉じて期限の到来の案内を画面の先頭に出す（自動で画面を移らない。入力を控えられるようにする）。
//   案内のリンクは、サーバーが渡した戻り先（GET で開いた業務の画面）があればそれを開き直す（Bolt 32e）
// - ブラウザの「戻る」（bfcache）やタブを見えるようにしたときは、localStorage の時刻を読み直す
document.addEventListener('DOMContentLoaded', () => {
  const dialog = document.getElementById('session-warning');
  const notice = document.getElementById('session-expired-notice');
  const noticeTemplate = document.getElementById('session-expired-template');
  const errorTemplate = document.getElementById('session-warning-error-template');
  if (!dialog || !notice || !noticeTemplate || !errorTemplate) {
    return;
  }
  const idleMs = Number(dialog.dataset.idleSeconds) * 1000;
  const lifetimeMs = Number(dialog.dataset.lifetimeRemainingSeconds) * 1000;
  if (!(idleMs > 0) || !Number.isFinite(lifetimeMs)) {
    return;
  }
  const WARN_BEFORE_MS = 5 * 60 * 1000;
  const MINUTE_MS = 60 * 1000;
  const SAFETY_MARGIN_MS = 10 * 1000;
  const STORAGE_KEY = 'cargotracker.session.lastRequestAt';
  const message = document.getElementById('session-warning-message');
  const errorRegion = document.getElementById('session-warning-error');
  const form = document.getElementById('session-warning-extend');
  const extendButton = form.querySelector('button[type="submit"]');
  const closeButton = form.querySelector('[data-session-warning-close]');

  const lifetimeDeadline = Date.now() + lifetimeMs - SAFETY_MARGIN_MS;
  let lastRequestAt = Date.now();
  let timer = null;
  let dismissedDeadline = null;
  let returnFocus = null;
  let shownKind = null;
  let expired = false;

  const store = (time) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(time));
    } catch (e) {
      // localStorage を使えないときは、このタブの時刻だけで数える
    }
  };

  const stored = () => {
    try {
      return Number(window.localStorage.getItem(STORAGE_KEY));
    } catch (e) {
      return Number.NaN;
    }
  };

  const deadline = () => {
    const idleDeadline = lastRequestAt + idleMs - SAFETY_MARGIN_MS;
    return idleDeadline < lifetimeDeadline
      ? { kind: 'idle', at: idleDeadline }
      : { kind: 'lifetime', at: lifetimeDeadline };
  };

  const minutesText = (remainingMs) =>
    remainingMs < MINUTE_MS ? '1 分未満' : `${Math.ceil(remainingMs / MINUTE_MS)} 分`;

  const clearError = () => errorRegion.replaceChildren();

  const render = (current, now) => {
    const template = current.kind === 'idle' ? dialog.dataset.idleMessage : dialog.dataset.lifetimeMessage;
    message.textContent = template.replace('{minutes}', minutesText(current.at - now));
    extendButton.hidden = current.kind !== 'idle';
  };

  const open = (current, now) => {
    const kindChanged = shownKind !== current.kind;
    render(current, now);
    shownKind = current.kind;
    if (dialog.open) {
      // 開いたまま 8 時間の警告に替わったら、隠れた延長のボタンからフォーカスを移す
      if (kindChanged && (document.activeElement === extendButton || !dialog.contains(document.activeElement))) {
        clearError();
        closeButton.focus();
      }
      return;
    }
    clearError();
    returnFocus = document.activeElement;
    dialog.showModal();
    (current.kind === 'idle' ? extendButton : closeButton).focus();
  };

  const close = () => {
    if (dialog.open) {
      dialog.close();
    }
  };

  // サーバーが渡した戻り先を、同じ origin の path と query に限って使う（多層防御。サーバーは業務の画面の path だけを渡す）
  const sameOriginPath = (value) => {
    if (!value) {
      return null;
    }
    try {
      const url = new URL(value, window.location.origin);
      return url.origin === window.location.origin ? url.pathname + url.search : null;
    } catch (e) {
      return null;
    }
  };

  const expire = () => {
    expired = true;
    window.clearTimeout(timer);
    close();
    // 画面の先頭（タブの順でも最初）に案内を置き、案内が背後の画面のフォーカスを隠さないよう scroll-padding を足す
    document.body.prepend(notice);
    notice.replaceChildren(noticeTemplate.content.cloneNode(true));
    // GET で開いた業務の画面なら、案内のリンクでその画面を開き直す。サーバーの session が切れていれば、失効の扱いが画面を覚えて
    // A-03 へ移し、ログインし直した後に戻る（Bolt 32e）。送信の後の画面などで戻り先がなければ、今どおり A-03 へ移る
    const link = notice.querySelector('[data-session-return]');
    const target = sameOriginPath(dialog.dataset.returnPath);
    if (link && target) {
      link.setAttribute('href', target);
    }
    document.documentElement.style.scrollPaddingTop = `${notice.offsetHeight}px`;
  };

  const unexpire = () => {
    expired = false;
    notice.replaceChildren();
    document.documentElement.style.scrollPaddingTop = '';
  };

  const tick = () => {
    window.clearTimeout(timer);
    if (expired) {
      return;
    }
    const now = Date.now();
    const current = deadline();
    if (now >= current.at) {
      expire();
      return;
    }
    const warnAt = current.at - WARN_BEFORE_MS;
    if (now < warnAt) {
      close();
      timer = window.setTimeout(tick, warnAt - now);
      return;
    }
    if (dismissedDeadline === current.at) {
      timer = window.setTimeout(tick, current.at - now);
      return;
    }
    open(current, now);
    timer = window.setTimeout(tick, Math.min(MINUTE_MS, current.at - now));
  };

  const requested = (time) => {
    lastRequestAt = time;
    dismissedDeadline = null;
    tick();
  };

  // ほかのタブや「戻る」の前の画面の request を取り込む（新しいときだけ）
  const catchUp = () => {
    const time = stored();
    if (Number.isFinite(time) && time > lastRequestAt) {
      if (expired && time + idleMs - SAFETY_MARGIN_MS > Date.now()) {
        unexpire();
      }
      requested(time);
    } else {
      tick();
    }
  };

  // 利用者が閉じた（「閉じる」・Esc）ときは延長しない。同じ期限ではもう開かない（期限が来たら案内に替わる）。
  // 延長やほかのタブの request で閉じたときは、次の期限でまた開く
  const dismiss = () => {
    dismissedDeadline = deadline().at;
  };
  dialog.addEventListener('cancel', dismiss);
  closeButton.addEventListener('click', () => {
    dismiss();
    close();
  });
  dialog.addEventListener('close', () => {
    shownKind = null;
    // close のイベントは遅れて届く。その間に警告を開き直していたら、フォーカスを奪わない
    if (!dialog.open && returnFocus && typeof returnFocus.focus === 'function') {
      returnFocus.focus();
    }
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (extendButton.disabled) {
      return;
    }
    clearError();
    extendButton.disabled = true;
    try {
      const response = await fetch(form.action, {
        method: 'POST',
        body: new URLSearchParams(new FormData(form)),
        redirect: 'manual',
      });
      if (response.status === 204) {
        const now = Date.now();
        store(now);
        // 期限の到来の案内を出した後に延長が届いたら（期限のぎわで押したとき）、案内を取り消す
        if (expired) {
          unexpire();
        }
        close();
        requested(now);
      } else if (response.type === 'opaqueredirect') {
        expire();
      } else {
        errorRegion.replaceChildren(errorTemplate.content.cloneNode(true));
      }
    } catch (e) {
      errorRegion.replaceChildren(errorTemplate.content.cloneNode(true));
    } finally {
      extendButton.disabled = false;
    }
  });

  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) {
      catchUp();
    }
  });
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) {
      catchUp();
    }
  });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      catchUp();
    }
  });

  store(lastRequestAt);
  tick();
});
