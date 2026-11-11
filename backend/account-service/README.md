# account-service

Executable Spring Boot service on port 8084. Owns the account views and zero-fund opening/closure API. Imports only its designated controllers and services from banking-kernel. The transfer service alone owns migrations and financial postings. The current simulation deliberately shares PostgreSQL for transactional ownership and foreign-key integrity; this is not database-per-service isolation. See docs/architecture.md.
