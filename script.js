const menu = document.querySelector('[data-menu]');
const links = document.querySelector('[data-links]');

if (menu && links) {
  menu.addEventListener('click', () => {
    const open = links.classList.toggle('open');
    menu.setAttribute('aria-expanded', String(open));
    menu.setAttribute(
      'aria-label',
      open ? 'Close navigation' : 'Open navigation'
    );
  });

  links.querySelectorAll('a').forEach(link =>
    link.addEventListener('click', () => {
      links.classList.remove('open');
      menu.setAttribute('aria-expanded', 'false');
      menu.setAttribute('aria-label', 'Open navigation');
    })
  );
}

document.querySelectorAll('a[href^="#"]').forEach(a => {
  a.addEventListener('click', e => {
    const el = document.querySelector(a.getAttribute('href'));

    if (el) {
      e.preventDefault();
      el.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
    }
  });
});

const contactForm = document.getElementById('contact-form');

if (contactForm) {
  const submitButton = contactForm.querySelector(
    'button[type="submit"]'
  );

  const successBox = document.getElementById('form-success');

  contactForm.addEventListener('submit', async e => {
    e.preventDefault();

    if (submitButton) {
      submitButton.disabled = true;
      submitButton.dataset.originalText =
        submitButton.innerHTML;

      submitButton.innerHTML = 'Sending…';
    }

    try {
      const response = await fetch(contactForm.action, {
        method: 'POST',
        body: new FormData(contactForm),
        headers: {
          Accept: 'application/json'
        }
      });

      if (!response.ok) {
        throw new Error('Form submission failed');
      }

      contactForm.reset();

      if (successBox) {
        successBox.textContent =
          'Thanks! Your message has been sent successfully.';

        successBox.hidden = false;

        successBox.scrollIntoView({
          behavior: 'smooth',
          block: 'center'
        });
      }

    } catch (error) {
      if (successBox) {
        successBox.textContent =
          'Sorry, your message could not be sent. Please try again.';

        successBox.hidden = false;

        successBox.scrollIntoView({
          behavior: 'smooth',
          block: 'center'
        });
      }

    } finally {
      if (submitButton) {
        submitButton.disabled = false;

        submitButton.innerHTML =
          submitButton.dataset.originalText ||
          'Send message →';
      }
    }
  });
}

const params = new URLSearchParams(
  window.location.search
);

const success = document.getElementById('form-success');

if (success && params.get('sent') === '1') {
  success.hidden = false;

  success.scrollIntoView({
    behavior: 'smooth',
    block: 'center'
  });
}
