# LAN discovery

When the user explicitly starts the laptop's secure HTTPS listener, the
companion optionally advertises `_laptopwatch._tcp.local.` with TXT keys
`id`, `name`, `v`, `fp`. This advertisement contains no private keys,
PINs or cameras. The QR provides signed desktop identity and certificate
fingerprint, while mDNS merely helps find the current IP.

Install the optional dependency `python -m pip install -e '.[discovery]'`
or include it in packaged sidecar dependencies. The user may need to allow
mDNS multicast UDP 5353 on their **trusted home LAN**; never automatically
open arbitrary firewall ports.

Android NsdManager MUST match advertised `id` and `fp` to previously
pinned values, verify responses with signed HTTPS and never treat anonymous
mDNS broadcasts as authentic. Other-network and cellular discovery requires
an authenticated signaling channel and ICE.
