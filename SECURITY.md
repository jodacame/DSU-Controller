# Security policy

DSU Controller opens a UDP port on the phone and sends controller input over the local network,
so its security properties are part of the product.

## What the app promises

- It opens exactly one socket: the DSU server on the local network.
- It never answers an address the user has not approved on screen.
- It sends nothing except DSU responses, and nothing to the internet.
- It contains no third-party libraries, analytics or advertising code.

A behaviour that breaks one of these promises is a security bug.

## Known limits

The DSU protocol has no encryption or authentication. Controller data travels in clear text on
the local network, and approval is by IP address, which another device on the same network could
forge. Use the app on networks you trust.

## Reporting a vulnerability

Please do not open a public issue for a security problem. Use GitHub's private vulnerability
reporting for this repository (Security tab, "Report a vulnerability"), and include:

- what you found and how to reproduce it;
- the app version, phone model and Android version;
- the impact you expect.

You will get an answer as soon as the maintainers can look at it, and credit in the release
notes if you want it.

## Supported versions

Only the latest release receives fixes.
