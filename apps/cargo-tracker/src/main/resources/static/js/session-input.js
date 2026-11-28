// 入力の控え（Bolt 32f、ACC-06、横断の受入条件の「未保存の入力は失われず再認証後に再開できる」、ADR-012、ADR-005 の例外）。
// - ログアウトしたときに送信していなかった入力を、ブラウザのそのタブ（sessionStorage）に控え、同じ利用者が同じ画面に戻ったら戻す。
//   業務の「下書き」（輸送要求版の状態など）とは別のもの。控えは送信前の入力の写しで、業務状態の正はサーバーにある
// - 写す: 警告の期限の到来（cargotracker:session-expired）、案内の「画面を開き直す」、業務の画面の POST のフォームの送信の前。
//   戻り先（data-return-path）のある画面で、初期の値から変えた欄だけ。password・hidden（CSRF のトークン）・ファイルは写さない
// - 捨てる: 戻したとき、送信が届いたとき（送信の次がログインした画面）、「入力していた内容を消す」、利用者が違うとき、写してから
//   8 時間、ログアウト（ヘッダーのボタン、ログアウトの後のログインの画面）、期限の到来の取り消し（cargotracker:session-unexpired）、
//   公開の画面を経ずにほかの画面へ移ったとき、形の合わない控え
// - 送信が拒まれた画面（A-03、CSRF の 403、413。main の data-session-input="rejected"）では控えに「拒まれた」印を付ける。
//   公開の画面でも、印のない画面（A-01、認可の 403 など）では付けない
// - 戻す値は .value・.checked で入れる（HTML として解釈しない）。知らせは先に置いた空の live region（aria-live）に入れる
(() => {
  const KEY = 'cargotracker.session.savedInput';
  const MAX_AGE_MS = 8 * 60 * 60 * 1000;
  // 読み込みの直後の live region の更新は、支援技術が画面の読み上げに重ねて捨てることがあるので、知らせだけ遅らせる
  const ANNOUNCE_DELAY_MS = 500;
  const VALUE_TYPES = new Set([
    'text', 'search', 'tel', 'url', 'email', 'number', 'date', 'datetime-local', 'time', 'month', 'week',
  ]);

  const wellFormed = (saved) => saved !== null && typeof saved === 'object'
    && typeof saved.user === 'string' && typeof saved.returnPath === 'string'
    && typeof saved.savedAt === 'number' && typeof saved.reason === 'string' && Array.isArray(saved.fields);

  const read = () => {
    try {
      const value = window.sessionStorage.getItem(KEY);
      const saved = value ? JSON.parse(value) : null;
      return wellFormed(saved) ? saved : null;
    } catch (e) {
      return null;
    }
  };
  const write = (saved) => {
    try {
      window.sessionStorage.setItem(KEY, JSON.stringify(saved));
    } catch (e) {
      // sessionStorage を使えないときは控えない
    }
  };
  const clear = () => {
    try {
      window.sessionStorage.removeItem(KEY);
    } catch (e) {
      // 何もしない
    }
  };

  const expiredByAge = (saved) => !(Date.now() - saved.savedAt < MAX_AGE_MS);

  // 戻り先の正規化: query から continue（ログインし直して戻ったときの印）を除き、ほかは生のまま残す
  const normalize = (path) => {
    if (!path) {
      return null;
    }
    const at = path.indexOf('?');
    if (at < 0) {
      return path;
    }
    const kept = path.slice(at + 1).split('&').filter((part) => part !== 'continue' && !part.startsWith('continue='));
    return kept.length > 0 ? `${path.slice(0, at)}?${kept.join('&')}` : path.slice(0, at);
  };

  // 戻り先は同じ origin の path と query に限って使う。// や /\ で始まる path は、リンクにするとほかの origin を指すので拒む
  // （session-timeout.js にも同じ確かめがある）
  const sameOriginPath = (value) => {
    if (!value) {
      return null;
    }
    try {
      const url = new URL(value, window.location.origin);
      const path = url.pathname + url.search;
      return url.origin === window.location.origin && /^\/(?![/\\])/.test(path) ? path : null;
    } catch (e) {
      return null;
    }
  };

  // form.method・form.action は同じ名前の欄に隠されることがあるので、属性で読む
  const methodOf = (form) => (form.getAttribute('method') || 'get').toLowerCase();

  const postForms = () => Array.from(document.querySelectorAll('main form')).filter((form) => methodOf(form) === 'post');

  const copyable = (element) => {
    if (element.tagName === 'TEXTAREA' || element.tagName === 'SELECT') {
      return true;
    }
    return element.tagName === 'INPUT'
      && (element.type === 'radio' || element.type === 'checkbox' || VALUE_TYPES.has(element.type));
  };

  const changed = (element) => {
    if (element.tagName === 'SELECT') {
      return Array.from(element.options).some((option) => option.selected !== option.defaultSelected);
    }
    if (element.type === 'radio' || element.type === 'checkbox') {
      return element.checked !== element.defaultChecked;
    }
    return element.value !== element.defaultValue;
  };

  const namedFields = (form, name) =>
    Array.from(form.elements).filter((element) => element.name === name && copyable(element));

  const capture = () => {
    const fields = [];
    let hasFile = false;
    postForms().forEach((form, formIndex) => {
      Array.from(form.elements).forEach((element) => {
        if (!element.name || element.disabled) {
          return;
        }
        if (element.type === 'file') {
          hasFile = hasFile || (element.files && element.files.length > 0);
          return;
        }
        if (!copyable(element) || !changed(element)) {
          return;
        }
        const index = namedFields(form, element.name).indexOf(element);
        if (element.type === 'radio' || element.type === 'checkbox') {
          fields.push({ form: formIndex, name: element.name, kind: 'check', value: element.value, checked: element.checked });
        } else if (element.tagName === 'SELECT') {
          const values = Array.from(element.selectedOptions).map((option) => option.value);
          fields.push({ form: formIndex, name: element.name, kind: 'select', index, values });
        } else {
          fields.push({ form: formIndex, name: element.name, kind: 'value', index, value: element.value });
        }
      });
    });
    return { fields, hasFile };
  };

  // 戻した欄・消した欄に input と change を出し、画面の表示（貨物種別の案内、提出の aria-disabled など）を追随させる
  const fire = (element) => {
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  };

  const restore = (saved) => {
    const forms = postForms();
    const touched = new Set();
    saved.fields.forEach((field) => {
      const form = forms[field.form];
      if (!form || typeof field.name !== 'string') {
        return;
      }
      const candidates = namedFields(form, field.name);
      if (field.kind === 'check') {
        const element = candidates.find((each) => each.value === field.value);
        if (element) {
          element.checked = field.checked === true;
          fire(element);
          touched.add(element);
        }
        return;
      }
      const element = candidates[field.index];
      if (!element) {
        return;
      }
      if (field.kind === 'select' && Array.isArray(field.values)) {
        Array.from(element.options).forEach((option) => {
          option.selected = field.values.includes(option.value);
        });
      } else if (field.kind === 'value' && typeof field.value === 'string') {
        element.value = field.value;
      } else {
        return;
      }
      fire(element);
      touched.add(element);
    });
    return touched;
  };

  const paragraph = (text) => {
    const p = document.createElement('p');
    p.className = 'mb-2';
    p.textContent = text;
    return p;
  };

  const button = (text, onClick) => {
    const element = document.createElement('button');
    element.type = 'button';
    element.className = 'btn btn-secondary btn-sm';
    element.textContent = text;
    element.addEventListener('click', onClick);
    return element;
  };

  const announce = (show) => {
    const later = () => window.setTimeout(show, ANNOUNCE_DELAY_MS);
    if (document.readyState === 'complete') {
      later();
    } else {
      window.addEventListener('load', later, { once: true });
    }
  };

  // 消すとボタンがなくなりフォーカスを失うので、知らせの領域に移して消した後の文言を読ませる（UI 設計）。中身の更新とフォーカスで
  // 2 回読まれないよう、差し替える間だけ aria-live を止める
  const settle = (region, text) => {
    region.setAttribute('aria-live', 'off');
    region.replaceChildren(paragraph(text));
    region.setAttribute('tabindex', '-1');
    region.focus();
    window.requestAnimationFrame(() => region.setAttribute('aria-live', 'polite'));
  };

  const notice = (region, nodes) => {
    const box = document.createElement('div');
    box.className = 'alert alert-info mb-3';
    nodes.forEach((node) => box.appendChild(node));
    region.replaceChildren(box);
  };

  const showRestored = (region, saved, touched) => {
    const nodes = [paragraph('前に入力していた内容を戻しました。まだ送信していません。内容を確かめてから送信してください。')];
    if (document.querySelector('main form[data-stepwise]')) {
      nodes.push(paragraph('すべての段階に戻しました。確認の段階でまとめて見直せます。'));
    }
    if (saved.hasFile === true) {
      nodes.push(paragraph('ファイルは選び直してください。'));
    }
    nodes.push(button('戻した入力を消す', () => {
      const forms = new Set(Array.from(touched).map((element) => element.form).filter(Boolean));
      forms.forEach((form) => form.reset());
      // reset は値を戻す前に reset のイベントを出すので、値が戻ってから欄ごとに知らせる
      window.setTimeout(() => touched.forEach(fire));
      settle(region, '戻した入力を消しました。');
    }));
    notice(region, nodes);
  };

  const showPending = (region, saved) => {
    const target = sameOriginPath(saved.returnPath);
    if (!target) {
      clear();
      return;
    }
    const link = document.createElement('a');
    link.href = target;
    link.className = 'btn btn-primary btn-sm me-2';
    link.textContent = '入力していた画面を開く';
    const actions = document.createElement('p');
    actions.className = 'mb-0';
    actions.appendChild(link);
    actions.appendChild(button('入力していた内容を消す', () => {
      clear();
      settle(region, '入力していた内容を消しました。');
    }));
    notice(region, [paragraph('送信できなかった入力があります。'), actions]);
  };

  // ログインした画面（警告の部分がある）
  const loggedIn = (dialog) => {
    const user = dialog.dataset.user;
    const returnPath = normalize(dialog.dataset.returnPath);
    const region = document.getElementById('session-input-status');

    const save = (reason) => {
      if (!user || !returnPath) {
        return;
      }
      const { fields, hasFile } = capture();
      if (fields.length === 0) {
        return;
      }
      write({ user, returnPath, savedAt: Date.now(), reason, passedPublic: false, hasFile, fields });
    };

    // 聞き手は戻す前に置く（戻す途中で失敗しても、次の期限の到来・送信・ログアウトで控えを扱える）
    document.addEventListener('cargotracker:session-expired', () => save('expired'));
    document.addEventListener('cargotracker:session-unexpired', () => {
      const current = read();
      if (current && current.reason === 'expired' && !current.passedPublic) {
        clear();
      }
    });
    document.addEventListener('click', (event) => {
      if (event.target instanceof Element && event.target.closest('[data-session-return]')) {
        save('reopen');
      }
    });
    document.addEventListener('submit', (event) => {
      const form = event.target;
      if (!(form instanceof HTMLFormElement)) {
        return;
      }
      if (new URL(form.getAttribute('action') || '', window.location.href).pathname.endsWith('/logout')) {
        clear();
        return;
      }
      if (!form.closest('main') || methodOf(form) !== 'post') {
        return;
      }
      // 拒まれた送信の控え（ほかの画面の入力）は、別の画面の送信で上書きしない。案内から開くか消すまで残す
      const current = read();
      if (current && current.passedPublic && current.returnPath !== returnPath) {
        return;
      }
      save('submit');
    }, true);

    let saved = read();
    if (saved) {
      const stale = saved.user !== user || expiredByAge(saved) || (saved.reason === 'submit' && !saved.passedPublic);
      if (stale) {
        clear();
        saved = null;
      }
    }
    if (saved && saved.returnPath === returnPath) {
      clear();
      try {
        const touched = restore(saved);
        if (region && touched.size > 0) {
          announce(() => showRestored(region, saved, touched));
        }
      } catch (e) {
        // 形の合わない控えは戻さない（控えはもう捨ててある）
      }
    } else if (saved && saved.passedPublic) {
      if (region) {
        announce(() => showPending(region, saved));
      }
    } else if (saved) {
      // 公開の画面を経ずにほかの画面へ移った（ログアウトしていない）。控えは要らない
      clear();
    }
  };

  // 公開の画面（A-03・A-01・403・413 など）
  const publicPage = () => {
    if (window.location.pathname.endsWith('/login') && new URLSearchParams(window.location.search).has('logout')) {
      clear();
      return;
    }
    const saved = read();
    if (!saved) {
      clear();
      return;
    }
    if (expiredByAge(saved)) {
      clear();
      return;
    }
    if (document.querySelector('main[data-session-input="rejected"]')) {
      saved.passedPublic = true;
      write(saved);
    }
    // 戻せる見込みがあるとき（送信が届いた控えでないとき）だけ、A-03 で戻すことを伝える
    const note = document.getElementById('session-input-note');
    if (note && note.dataset.message && (saved.reason !== 'submit' || saved.passedPublic)) {
      note.textContent = note.dataset.message;
    }
  };

  document.addEventListener('DOMContentLoaded', () => {
    const dialog = document.getElementById('session-warning');
    if (dialog) {
      loggedIn(dialog);
    } else {
      publicPage();
    }
  });
})();
