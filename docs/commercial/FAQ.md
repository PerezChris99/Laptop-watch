# LaptopWatch — support FAQ draft

**Is it available for download?** Not yet. The repository is a development prototype; signed builds and store acceptance are incomplete.

**Does it work without Internet?** Local-network operation is the target. Secure remote operation needs a reachable peer path or an optional configured relay. The current legacy Python service is not suitable for public exposure.

**Will it secretly record people?** No covert surveillance mode should be shipped. Camera and microphone use must be clearly visible, user-controlled and legal. The user remains responsible for consent.

**Does my laptop have GPS?** Not necessarily. The existing prototype previously returned simulated coordinates; live mock values have been removed from the Python source. Precise location requires an actual trusted source and explicit consent.

**Is my video stored in the cloud?** Local-first storage is the intended default. Optional remote features may relay encrypted media and process service metadata. Check final privacy policy and service-provider list before using a release build.

**What if my laptop sleeps or the lid closes?** The camera may become unavailable. Power management policies vary by hardware and OS; disabling sleep is not guaranteed and can create thermal/battery risks. Do not claim camera operation with a physically blocked/closed lid.

**How do I unpair a lost phone?** A desktop revoke/remove-device control is required before launch. The standalone Python security module supports key revocation but it is not wired into a desktop UI.

**What happens after a false alarm?** The future person detector should help distinguish moving curtains, TVs, pets and lighting changes from people. Until that classifier is implemented and measured, false alarms are possible.

**Does it prevent theft?** No. It is an additional deterrence and evidence aid, not physical theft protection or replacement for backups, full-disk encryption and a login password.

**Where can I get help?** A public support address and response policy must be published before commercial launch.
