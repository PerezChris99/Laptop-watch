'use strict';
// URL fragment never reaches HTTP access logs. Remove before loading Paddle.
const status = document.querySelector('#status');
const button = document.querySelector('#checkout');
const fragment = new URLSearchParams(location.hash.slice(1));
const price = fragment.get('price_id');
const ticket = fragment.get('ticket');
try { history.replaceState(null, '', location.pathname); } catch (_) {}
const message = (value) => { status.textContent = value; };
const valid = fragment.size === 2 && /^[a-zA-Z0-9_.-]{20,2048}$/.test(ticket || '') &&
  /^pri_[a-z0-9]{26}$/.test(price || '');

async function load() {
  if (!valid) { message('Invalid or expired invitation. Open a new checkout from your laptop.'); return; }
  let config;
  try {
    const response = await fetch('/v1/billing/public-config', {cache:'no-store', credentials:'omit'});
    if (!response.ok) throw new Error('unavailable');
    config = await response.json();
    if (!Array.isArray(config.prices) || !config.prices.includes(price) ||
        !/^((test)|(live))_[A-Za-z0-9]+$/.test(config.client_token)) throw new Error('price unavailable');
    const result = await fetch('/v1/billing/checkout-validation', {
      method:'POST', credentials:'omit', cache:'no-store',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({price_id:price,ticket})
    });
    if (!result.ok || (await result.json()).valid !== true) throw new Error('expired or redeemed');
  } catch (_) { message('Secure checkout is unavailable or the invitation expired. Request a new one.'); return; }
  const script = document.createElement('script');
  script.src = 'https://cdn.paddle.com/paddle/v2/paddle.js';
  script.referrerPolicy = 'no-referrer';
  try {
    await new Promise((resolve,reject) => {
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
    if (!window.Paddle) throw new Error('Paddle unavailable');
    if (config.environment === 'sandbox') window.Paddle.Environment.set('sandbox');
    window.Paddle.Initialize({token:config.client_token, eventCallback: (event) => {
      if (event.name === 'checkout.completed') {
        button.disabled = true;
        message('Checkout complete. Payment confirmation is processing. Reopen LaptopWatch and refresh your license.');
      }
      if (event.name === 'checkout.closed') message('Checkout closed. You may retry while the invitation is valid.');
    }});
    button.disabled = false;
    message('Checkout is ready. Payment is handled directly by Paddle.');
    button.addEventListener('click', () => {
      button.disabled = true;
      try {
        window.Paddle.Checkout.open({
          items:[{priceId:price, quantity:1}],
          customData:{laptopwatch_checkout_ticket:ticket},
          settings:{displayMode:'overlay', theme:'light'}
        });
        button.disabled = false;
      } catch (_) { message('Could not open checkout. Request another authorization.'); }
    });
  } catch (_) { message('Paddle checkout is unavailable. Try again later.'); }
}
load();
