// Báo lỗi trước khi gửi để quản trị giữ nguyên các ảnh đã chọn trong biểu mẫu.
(() => {
  const form = document.querySelector('form[enctype="multipart/form-data"]');
  if (!form) return;
  const inputs = [...form.querySelectorAll('input[type="file"]')];
  const mb = 1024 * 1024;
  function validate() {
    let total = 0;
    for (const input of inputs) {
      input.setCustomValidity('');
      for (const file of input.files) {
        total += file.size;
        if (file.size > 10 * mb) {
          input.setCustomValidity(`Ảnh “${file.name}” vượt 10 MB. Hãy giảm dung lượng ảnh rồi chọn lại.`);
        }
      }
    }
    // Chừa 1 MB cho nội dung chữ và các header multipart của biểu mẫu.
    if (total > 199 * mb && inputs.length) {
      inputs[0].setCustomValidity('Tổng ảnh quá lớn. Hãy chọn tổng dung lượng dưới 199 MB để lưu cùng nội dung biểu mẫu.');
    }
  }
  form.addEventListener('change', validate);
  form.addEventListener('submit', event => {
    validate();
    if (!form.reportValidity()) event.preventDefault();
  });
})();
