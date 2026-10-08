/*
 * JavaScript này chỉ phục vụ biểu tượng mắt: HTML không thể tự đổi kiểu của ô
 * mật khẩu từ password sang text. Các luồng nghiệp vụ vẫn do Spring MVC xử lý.
 */
document.addEventListener('click', (event) => {
  const button = event.target.closest('[data-toggle-password]');
  if (!button) return;
  const input = document.getElementById(button.dataset.togglePassword);
  if (!input) return;
  const visible = input.type === 'password';
  input.type = visible ? 'text' : 'password';
  button.setAttribute('aria-pressed', String(visible));
  button.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
  button.setAttribute('title', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
  button.querySelector('.eye-hidden')?.classList.toggle('hidden', visible);
  button.querySelector('.eye-visible')?.classList.toggle('hidden', !visible);
});

/* HTML không tự cập nhật phần tóm tắt sau khi tick checkbox; đoạn này chỉ hiển thị các sản phẩm đã chọn. */
function updateProductSummary(selector) {
  const summary = selector.querySelector('[data-product-summary]');
  if (!summary) return;
  const selected = [...selector.querySelectorAll('input[name="productIds"]:checked')]
    .map((checkbox) => checkbox.closest('label')?.querySelector('span')?.textContent.trim())
    .filter(Boolean);
  summary.textContent = selected.length ? selected.join(' · ') : 'Chọn sản phẩm đang có';
}

document.addEventListener('change', (event) => {
  if (!event.target.matches('[data-product-selector] input[name="productIds"]')) return;
  updateProductSummary(event.target.closest('[data-product-selector]'));
});

document.querySelectorAll('[data-product-selector]').forEach(updateProductSummary);

if (false) { // Mã cũ chỉ được giữ làm tham chiếu và không bao giờ thực thi.
(function () {
  'use strict';

  /* ── Mobile nav toggle ── */
  const menuBtn = document.getElementById('mobile-menu-btn');
  const mobileNav = document.getElementById('mobile-nav');
  if (menuBtn && mobileNav) {
    menuBtn.addEventListener('click', () => {
      mobileNav.classList.toggle('hidden');
      menuBtn.setAttribute('aria-expanded', String(!mobileNav.classList.contains('hidden')));
    });
  }

  /* ── Date validation: start ≤ end, both ≥ today ── */
  function setupDatePair(startId, endId) {
    const s = document.getElementById(startId);
    const en = document.getElementById(endId);
    if (!s || !en) return;
    const today = new Date().toISOString().split('T')[0];
    s.min = today;
    en.min = today;
    s.addEventListener('change', () => { if (en.value && en.value < s.value) en.value = s.value; en.min = s.value || today; });
    en.addEventListener('change', () => { if (s.value && s.value > en.value) s.value = en.value; });
  }
  setupDatePair('start', 'end');
  setupDatePair('pickup', 'returnDate');

  /* ── Delivery address toggle ── */
  const fulfilSelect = document.querySelector('[name="fulfilment"]');
  const addressRow = document.getElementById('address-row');
  function toggleAddress() {
    if (!fulfilSelect || !addressRow) return;
    addressRow.classList.toggle('hidden', fulfilSelect.value !== 'DELIVERY');
    const addrInput = addressRow.querySelector('input');
    if (addrInput) addrInput.required = fulfilSelect.value === 'DELIVERY';
  }
  if (fulfilSelect) { fulfilSelect.addEventListener('change', toggleAddress); toggleAddress(); }

  /* ── Auto-dismiss flash alerts ── */
  document.querySelectorAll('[data-auto-dismiss]').forEach(el => {
    const delay = Number(el.dataset.autoDismiss) || 3000;
    setTimeout(() => el.classList.add('opacity-0', 'transition-opacity', 'duration-500'), delay);
    setTimeout(() => el.remove(), delay + 600);
  });

  /* ── Confirm dangerous actions ── */
  document.querySelectorAll('[data-confirm]').forEach(el => {
    el.addEventListener('click', (e) => {
      if (!confirm(el.dataset.confirm)) e.preventDefault();
    });
  });

  /* ── In đơn thuê ── */
  const printOrderButton = document.querySelector('[data-print-order]');
  const printSuccess = document.getElementById('print-success');
  if (printOrderButton && printSuccess) {
    printOrderButton.addEventListener('click', () => {
      window.print();
      printSuccess.classList.remove('hidden');
      printSuccess.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  /* ── Số thứ tự đơn và sản phẩm trong các bảng ── */
  document.querySelectorAll('table').forEach(table => {
    const headerRow = table.querySelector('tr');
    if (!headerRow || headerRow.querySelector('[data-order-number]')) return;
    const headerText = headerRow.textContent || '';
    if (!headerText.includes('Mã đơn') && !headerText.includes('Danh sách bàn giao')) return;
    const header = document.createElement('th');
    header.dataset.orderNumber = 'true';
    header.textContent = 'STT';
    headerRow.prepend(header);
    let number = 1;
    table.querySelectorAll('tr').forEach(row => {
      if (!row.querySelector('td')) return;
      const cell = document.createElement('td');
      cell.className = 'font-medium text-stone-500';
      cell.textContent = String(number++);
      row.prepend(cell);
    });
  });

  /* ── QR scan: focus input on / key ── */
  document.addEventListener('keydown', (e) => {
    if (e.key === '/' && document.activeElement.tagName !== 'INPUT') {
      e.preventDefault();
      const scanInput = document.getElementById('scan-input');
      if (scanInput) scanInput.focus();
    }
  });

  /* ── Print shortcut ── */
  document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
      /* let browser default handle it */
    }
  });

  /* ── Smooth scroll for anchor links ── */
  document.querySelectorAll('a[href^="#"]').forEach(a => {
    a.addEventListener('click', e => {
      const id = a.getAttribute('href').slice(1);
      const el = document.getElementById(id);
      if (el) { e.preventDefault(); el.scrollIntoView({ behavior: 'smooth' }); }
    });
  });

  /* ── Ảnh nội dung xuất hiện theo chuyển động; giữ nguyên ảnh phục vụ quét mã ── */
  const motionImages = document.querySelectorAll('img:not([data-no-motion])');
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches && 'IntersectionObserver' in window) {
    const revealImage = (entries, observer) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('image-revealed');
        observer.unobserve(entry.target);
      });
    };
    const imageObserver = new IntersectionObserver(revealImage, { threshold: 0.08, rootMargin: '0px 0px -24px' });
    motionImages.forEach((image, index) => {
      image.classList.add('image-motion');
      image.style.transitionDelay = `${Math.min(index % 6, 5) * 90}ms`;
      imageObserver.observe(image);
    });
  }
})();
}
