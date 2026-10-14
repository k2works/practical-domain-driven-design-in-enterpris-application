// 入力の控え（Bolt 32f、ACC-06、横断の受入条件の「未保存の入力は失われず再認証後に再開できる」、ADR-012、ADR-005 の例外）。
// - ログアウトしたときに送信していなかった入力を、ブラウザのそのタブ（sessionStorage）に控え、同じ利用者が同じ画面に戻ったら戻す。
//   業務の「下書き」（輸送要求版の状態など）とは別のもの。控えは送信前の入力の写しで、業務状態の正はサーバーにある
// - 写す: 警告の期限の到来（cargotracker:session-expired）、案内の「画面を開き直す」、業務の画面の POST のフォームの送信の前。
//   戻り先（data-return-path）のある画面で、初期の値から変えた欄だけ。password・hidden（CSRF のトークン）・ファイルは写さない
// - 捨てる: 戻したとき、送信が届いたとき（送信の次がログインした画面）、「消す」、利用者が違うとき、写してから 8 時間、
//   ログアウト、期限の到来の取り消し（cargotracker:session-unexpired）、公開の画面を経ずにほかの画面へ移ったとき
// - 公開の画面（A-03・A-01・403 など。layout/public）では控えに「公開の画面を経た」印を付ける（送信が拒まれた・ログアウトした）
// - 戻す値は .value・.checked で入れる（HTML として解釈しない）。知らせは先に置いた空の live region（aria-live）に入れる
(() => {
  const KEY = 'cargotracker.session.savedInput';
  const MAX_AGE_MS = 8 * 60 * 60 * 1000;
  const VALUE_TYPES = new Set([
    'text', 'search', 'tel', 'url', 'email', 'number', 'date', 'datetime-local', 'time', 'month', 'week',
  ]);

  const read = () => {
    try {
      const value = window.sessionStorage.getItem(KEY);
      return value ? JSON.parse(value) : null;
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

  // 戻り先は同じ origin の path と query に限って使う
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

  const postForms = () =>
    Array.from(document.querySelectorAll('main form')).filter((form) => form.method.toLowerCase() === 'post');

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

  const fire = (element) => {
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  };

  const restore = (saved) => {
    const forms = postForms();
    const touched = new Set();
    saved.fields.forEach((field) => {
      const form = forms[field.form];
      if (!form) {
        return;
      }
      const candidates = namedFields(form, field.name);
      if (field.kind === 'check') {
        const element = candidates.find((each) => each.value === field.value);
        if (element) {
          element.checked = field.checked;
          fire(element);
          touched.add(form);
        }
      } else {
        const element = candidates[field.index];
        if (!element) {
          return;
        }
        if (field.kind === 'select') {
          Array.from(element.options).forEach((option) => {
            option.selected = field.values.includes(option.value);
          });
        } else {
          element.value = field.value;
        }
        fire(element);
        touched.add(form);
      }
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

  // 「消す」でボタンがなくなるとフォーカスを失うので、知らせの領域に移して消した後の文言を読ませる（UI 設計）
  const settle = (region, text) => {
    region.replaceChildren(paragraph(text));
    region.setAttribute('tabindex', '-1');
    region.focus();
  };

  const notice = (region, nodes) => {
    const box = document.createElement('div');
    box.className = 'alert alert-info mb-3';
    nodes.forEach((node) => box.appendChild(node));
    region.replaceChildren(box);
  };

  const showRestored = (region, saved, touched) => {
    const nodes = [paragraph('ログアウトの前の入力を戻しました。まだ送信していません。内容を確かめてから送信してください。')];
    if (saved.hasFile) {
      nodes.push(paragraph('ファイルは選び直してください。'));
    }
    nodes.push(button('戻した入力を消す', () => {
      touched.forEach((form) => form.reset());
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
    link.textContent = '画面を開く';
    const actions = document.createElement('p');
    actions.className = 'mb-0';
    actions.appendChild(link);
    actions.appendChild(button('消す', () => {
      clear();
      settle(region, '入力していた内容を消しました。');
    }));
    notice(region, [paragraph('ログアウトの前に入力していた内容があります。'), actions]);
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

    let saved = read();
    if (saved) {
      const stale = saved.user !== user
        || !(Date.now() - saved.savedAt < MAX_AGE_MS)
        || (saved.reason === 'submit' && !saved.passedPublic);
      if (stale) {
        clear();
        saved = null;
      }
    }
    if (saved && saved.returnPath === returnPath) {
      clear();
      const touched = restore(saved);
      if (region) {
        showRestored(region, saved, touched);
      }
    } else if (saved && saved.passedPublic) {
      if (region) {
        showPending(region, saved);
      }
    } else if (saved) {
      // 公開の画面を経ずにほかの画面へ移った（ログアウトしていない）。控えは要らない
      clear();
    }

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
      if (new URL(form.action, window.location.origin).pathname.endsWith('/logout')) {
        clear();
        return;
      }
      if (form.closest('main') && form.method.toLowerCase() === 'post') {
        save('submit');
      }
    }, true);
  };

  // 公開の画面（A-03・A-01・403 など）
  const publicPage = () => {
    const saved = read();
    if (!saved) {
      return;
    }
    saved.passedPublic = true;
    write(saved);
    const note = document.getElementById('session-input-note');
    if (note && note.dataset.message) {
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
