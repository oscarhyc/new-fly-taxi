# Live-server harvest (2026-10-20)

The production service is scheduled to stop on 31 December; driver accounts stop from
26 October. Everything below was captured while the service was still live, using the
operator's own driver account, read-only.

| file | what |
|---|---|
| `live/driver_config_api.sqlite` | live driver config DB (20 tables, config_version 20260916) |
| `live/driver_config_core01.sqlite` | same content from the release-core host |
| `live/config_driver_*.json` | raw `/v6/api/get_config_sqlite` responses (presigned S3 URLs) |

Derived, curated exports (business parameters only — no vendor copy text, no hotline
numbers, no fleet owner data) live in `product/spec/reference-data/`.

Not captured on purpose: passenger personal data, payment data, and anything requiring
order creation or automation.
