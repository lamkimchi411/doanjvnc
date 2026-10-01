/* Cổ Việt Lâu – UI helpers */
(function () {
  'use strict';

  /* ── Password toggle ── */
  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-toggle-password]');
    if (!btn) return;
    const input = document.getElementById(btn.dataset.togglePassword);
    if (!input) return;
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.setAttribute('aria-pressed', String(show));
    // update icon
    const eyeOpen = btn.querySelector('.eye-open');
    const eyeClosed = btn.querySelector('.eye-closed');
    if (eyeOpen) eyeOpen.classList.toggle('hidden', !show);
    if (eyeClosed) eyeClosed.classList.toggle('hidden', show);
  });

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
    setTimeout(() => el.classList.add('opacity-0', 'transition-opacity', 'duration-500'), 3000);
    setTimeout(() => el.remove(), 3600);
  });

  /* ── Confirm dangerous actions ── */
  document.querySelectorAll('[data-confirm]').forEach(el => {
    el.addEventListener('click', (e) => {
      if (!confirm(el.dataset.confirm)) e.preventDefault();
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
})();
