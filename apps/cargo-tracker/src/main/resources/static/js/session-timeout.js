// session の警告（Bolt 32b、ACC-06、WCAG 2.2.1、UI 設計の共通部品「session の警告」）。
// - 残り時間はサーバーが画面に出した秒数（無操作の期限、8 時間の上限までの残り）から、ブラウザの時計で数える。残りを問い合わせる
//   定期の request は送らない（問い合わせ自体が無操作の期限を延ばしてしまう）
// - 無操作の期限は「最後の request の時刻 + 無操作の秒数」。最後の request は画面の読み込みと延長で、時刻を localStorage に置いて
//   タブの間でそろえる（置くのはミリ秒の時刻だけ。業務データは置かない）。8 時間の期限は「読み込みの時刻 + 残りの秒数」
// - 早いほうの期限の 5 分前に警告を開く。無操作の警告は「ログインを延長する」にフォーカスを移し、8 時間の警告は延長を出さない
// - 延長は htmx でなく fetch で送る（302 を opaqueredirect として受け、失効を見分けるため）。204 なら閉じ、302 なら期限の到来、
//   それ以外（403・500・通信の失敗）は「延長できませんでした」を示して延長のボタンを残す
// - 期限を過ぎたら、モーダルを閉じて期限の到来の案内を出す（自動で画面を移らない。入力を控えられるようにする）
document.addEventListener('DOMContentLoaded', () => {
  const dialog = document.getElementById('session-warning');
  const notice = document.getElementById('session-expired-notice');
  const noticeTemplate = document.getElementById('session-expired-template');
  if (!dialog || !notice || !noticeTemplate) {
    return;
  }
  const WARN_BEFORE_MS = 5 * 60 * 1000;
  const MINUTE_MS = 60 * 1000;
  const STORAGE_KEY = 'cargotracker.session.lastRequestAt';
  const message = document.getElementById('session-warning-message');
  const error = document.getElementById('session-warning-error');
  const form = document.getElementById('session-warning-extend');
  const extendButton = form.querySelector('button[type="submit"]');
  const closeButton = form.querySelector('[data-session-warning-close]');

  const idleMs = Number(dialog.dataset.idleSeconds) * 1000;
  const lifetimeDeadline = Date.now() + Number(dialog.dataset.lifetimeRemainingSeconds) * 1000;
  let lastRequestAt = Date.now();
  let timer = null;
  let dismissedDeadline = null;
  let returnFocus = null;
  let expired = false;

  const store = (time) => {
    try {
      window.localStorage.setItem(STORAGE_KEY, String(time));
    } catch (e) {
      // localStorage を使えないときは、このタブの時刻だけで数える
    }
  };

  const deadline = () => {
    const idleDeadline = lastRequestAt + idleMs;
    return idleDeadline < lifetimeDeadline
      ? { kind: 'idle', at: idleDeadline }
      : { kind: 'lifetime', at: lifetimeDeadline };
  };

  const minutesText = (remainingMs) =>
    remainingMs < MINUTE_MS ? '1 分未満' : `${Math.ceil(remainingMs / MINUTE_MS)} 分`;

  const render = (current, now) => {
    const template = current.kind === 'idle' ? dialog.dataset.idleMessage : dialog.dataset.lifetimeMessage;
    message.textContent = template.replace('{minutes}', minutesText(current.at - now));
    extendButton.hidden = current.kind !== 'idle';
  };

  const open = (current, now) => {
    render(current, now);
    if (dialog.open) {
      return;
    }
    error.hidden = true;
    returnFocus = document.activeElement;
    dialog.showModal();
    (current.kind === 'idle' ? extendButton : closeButton).focus();
  };

  const close = () => {
    if (dialog.open) {
      dialog.close();
    }
  };

  const expire = () => {
    expired = true;
    window.clearTimeout(timer);
    close();
    notice.replaceChildren(noticeTemplate.content.cloneNode(true));
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
    if (returnFocus && typeof returnFocus.focus === 'function') {
      returnFocus.focus();
    }
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    error.hidden = true;
    try {
      const response = await fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        credentials: 'same-origin',
        redirect: 'manual',
      });
      if (response.status === 204) {
        const now = Date.now();
        store(now);
        close();
        requested(now);
      } else if (response.type === 'opaqueredirect') {
        expire();
      } else {
        error.hidden = false;
      }
    } catch (e) {
      error.hidden = false;
    }
  });

  window.addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY || expired) {
      return;
    }
    const time = Number(event.newValue);
    if (Number.isFinite(time) && time > lastRequestAt) {
      requested(time);
    }
  });

  store(lastRequestAt);
  tick();
});
