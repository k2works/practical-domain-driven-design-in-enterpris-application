// 見積依頼の作成画面（C-03）の振る舞い。JavaScript がなくても、提出すればサーバーが同じ誤りをエラー要約で示す。
// - エラー要約があれば、要約へフォーカスを移す（UI 設計 C-03）
// - 貨物種別に一般以外を選ぶと、対象外と手動窓口の案内を出し、提出ボタンを aria-disabled にして理由を関連付ける（Q-INV-02）
document.addEventListener('DOMContentLoaded', () => {
  const summary = document.getElementById('error-summary');
  if (summary) {
    summary.focus();
  }

  const categories = document.querySelectorAll('input[name="cargoCategory"]');
  const notice = document.getElementById('cargo-category-notice');
  const submit = document.getElementById('submit');
  if (categories.length === 0 || !notice || !submit) {
    return;
  }
  const update = () => {
    const checked = document.querySelector('input[name="cargoCategory"]:checked');
    const special = checked !== null && checked.value !== 'GENERAL';
    notice.hidden = !special;
    if (special) {
      submit.setAttribute('aria-disabled', 'true');
      submit.setAttribute('aria-describedby', 'cargo-category-notice');
    } else {
      submit.removeAttribute('aria-disabled');
      submit.removeAttribute('aria-describedby');
    }
  };
  categories.forEach((category) => category.addEventListener('change', update));
  update();
});

// 見積依頼の段階入力（C-03、Bolt 8 のプロトタイプ、UI-HO-04）。R1.0 で下書きを保存するまでは、1 つのフォームの中で段階を切り替える。
// - JavaScript がないときは、段階の一覧と「次へ」を出さず、全段階を 1 画面で示す（テンプレートの hidden のまま）
// - 段階を移ったら、その段階の見出しへフォーカスを移す。現在の段階は段階の一覧で aria-current="step" にする
// - 確認の段階では、入力の内容をまとめて示す。値は textContent で入れ、HTML として解釈しない
// - エラー要約のリンクを選ぶと、その項目のある段階を開いてフォーカスを移す。誤りがあれば最初の誤りの段階を開く
document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('form[data-stepwise]');
  const nav = document.getElementById('step-nav');
  if (!form || !nav) {
    return;
  }
  const steps = Array.from(form.querySelectorAll('.app-step'));
  const links = Array.from(nav.querySelectorAll('a'));
  const summary = document.getElementById('confirm-summary');

  const valueOf = (element) => {
    if (element.tagName === 'FIELDSET') {
      const checked = element.querySelector('input:checked');
      const label = checked ? form.querySelector(`label[for="${checked.id}"]`) : null;
      return label ? label.textContent.trim() : '（未選択）';
    }
    if (element.type === 'file') {
      const names = Array.from(element.files).map((file) => file.name);
      return names.length > 0 ? names.join('、') : '（なし）';
    }
    return element.value.trim() !== '' ? element.value.trim() : '（未入力）';
  };

  const renderSummary = () => {
    summary.replaceChildren();
    form.querySelectorAll('[data-summary-label]').forEach((element) => {
      const term = document.createElement('dt');
      term.textContent = element.dataset.summaryLabel;
      const value = document.createElement('dd');
      value.textContent = valueOf(element);
      summary.append(term, value);
    });
  };

  const show = (index, focusHeading) => {
    steps.forEach((step, i) => {
      step.hidden = i !== index;
    });
    links.forEach((link, i) => {
      if (i === index) {
        link.setAttribute('aria-current', 'step');
      } else {
        link.removeAttribute('aria-current');
      }
    });
    if (index === steps.length - 1) {
      renderSummary();
    }
    if (focusHeading) {
      steps[index].querySelector('h2').focus();
    }
  };

  nav.hidden = false;
  form.querySelectorAll('.app-step-actions').forEach((actions) => {
    actions.hidden = false;
  });
  form.querySelectorAll('[data-step-next]').forEach((button) => {
    const index = steps.indexOf(button.closest('.app-step'));
    button.addEventListener('click', () => show(index + 1, true));
  });
  links.forEach((link, index) => {
    link.addEventListener('click', (event) => {
      event.preventDefault();
      show(index, true);
    });
  });

  const stepOf = (element) => steps.findIndex((step) => step.contains(element));
  const errorLinks = Array.from(document.querySelectorAll('#error-summary a[href^="#"]'));
  errorLinks.forEach((link) => {
    link.addEventListener('click', (event) => {
      const target = document.getElementById(link.getAttribute('href').substring(1));
      if (!target) {
        return;
      }
      event.preventDefault();
      show(stepOf(target), false);
      target.focus();
    });
  });
  const firstError = errorLinks.length > 0 ? document.getElementById(errorLinks[0].getAttribute('href').substring(1)) : null;
  show(firstError ? Math.max(stepOf(firstError), 0) : 0, false);
});
