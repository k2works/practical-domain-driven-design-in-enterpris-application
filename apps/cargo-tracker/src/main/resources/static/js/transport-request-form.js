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
