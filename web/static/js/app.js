// TutorApp front-end behavior.
// Kept intentionally tiny: this app is server-rendered, so JS only handles
// small UX niceties, never core logic (all real actions are plain form POSTs).

document.addEventListener('DOMContentLoaded', function () {
  // Any <select class="auto-submit"> submits its enclosing form on change.
  // Used by the subject filter dropdown on the "Find a Tutor" page.
  document.querySelectorAll('select.auto-submit').forEach(function (el) {
    el.addEventListener('change', function () {
      el.form.submit();
    });
  });
});
