package pl.zzpj.subscription_service.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pl.zzpj.subscription_service.domain.token.TokenOperation;
import pl.zzpj.subscription_service.domain.token.TokenReservation;
import pl.zzpj.subscription_service.domain.token.reservation.TokenReservationStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "token_reservations", schema = "subscription_schema")
public class TokenReservationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private TokenOperation operation;

    @Column(name = "tokens", nullable = false)
    private int tokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TokenReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "external_operation_id")
    private String externalOperationId;

    protected TokenReservationEntity() {
    }

    public TokenReservationEntity(
            UUID id,
            String userId,
            TokenOperation operation,
            int tokens,
            TokenReservationStatus status,
            Instant createdAt,
            Instant expiresAt,
            Instant completedAt,
            String externalOperationId
    ) {
        this.id = id;
        this.userId = userId;
        this.operation = operation;
        this.tokens = tokens;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.completedAt = completedAt;
        this.externalOperationId = externalOperationId;
    }

    public static TokenReservationEntity from(
            TokenReservation reservation,
            Instant createdAt,
            String externalOperationId
    ) {
        return new TokenReservationEntity(
                reservation.reservationId(),
                reservation.userId(),
                reservation.operation(),
                reservation.tokens(),
                TokenReservationStatus.RESERVED,
                createdAt,
                reservation.expiresAt(),
                null,
                externalOperationId
        );
    }

    public TokenReservation toDomain() {
        return new TokenReservation(id, userId, operation, tokens, expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public int getTokens() {
        return tokens;
    }

    public TokenOperation getOperation() {
        return operation;
    }

    public TokenReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void markConsumed(Instant completedAt) {
        this.status = TokenReservationStatus.CONSUMED;
        this.completedAt = completedAt;
    }

    public void markReleased(Instant completedAt) {
        this.status = TokenReservationStatus.RELEASED;
        this.completedAt = completedAt;
    }
}
