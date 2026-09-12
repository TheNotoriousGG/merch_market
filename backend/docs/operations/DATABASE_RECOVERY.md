# Database backup and recovery assumptions

Обновлено: 19 августа 2026 года.

Этот документ фиксирует требования к будущему managed PostgreSQL provider. Он не объявляет локальный Compose production-ready backup system.

## Цели

- Availability target: 99.9%.
- RPO: не более 5 минут.
- RTO: не более 30 минут.
- Continuous WAL archiving/PITR и ежедневный provider backup обязательны.
- Backups, WAL и snapshots шифруются отдельными provider-managed keys/policies.

## Обязательные проверки до production

1. Автоматический backup и retention policy включены infrastructure-as-code.
2. PITR восстановление в новый isolated instance проверено на целевом объёме.
3. После restore выполняются Flyway validation, row-count/business reconciliation и smoke tests read-only credentials.
4. DNS/secret switch выполняется только после sign-off; старый instance остаётся read-only до окончания rollback window.
5. Restore drill проводится регулярно, а фактические RPO/RTO сохраняются как operational evidence.
6. Owner, migrator и runtime credentials после restore остаются раздельными и ротируются при подозрении на compromise.

## Migration rollback

- По умолчанию rollback — forward fix новой migration.
- Expand migration применяется до совместимого application deploy.
- Contract migration разрешена только после удаления old readers/writers и проверки telemetry.
- Restore из backup не используется как обычный rollback schema change: это аварийная процедура с потенциальной потерей данных до RPO.
