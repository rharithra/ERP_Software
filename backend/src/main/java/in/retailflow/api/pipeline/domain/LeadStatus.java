package in.retailflow.api.pipeline.domain;

import in.retailflow.api.common.exception.ErrorCodes;
import in.retailflow.api.common.exception.RetailflowException;
import org.springframework.http.HttpStatus;

public enum LeadStatus {
    NEW,
    CONTACTED,
    QUALIFIED,
    QUOTATION,
    NEGOTIATION,
    WON,
    LOST;

    public void requireTransition(LeadStatus next) {
        if (this == next) {
            return;
        }
        if (this == WON || this == LOST) {
            throw invalid("A closed lead cannot change status");
        }
        if (next == LOST) {
            return;
        }
        boolean allowed = switch (this) {
            case NEW -> next == CONTACTED || next == QUALIFIED;
            case CONTACTED -> next == QUALIFIED || next == QUOTATION;
            case QUALIFIED -> next == QUOTATION || next == NEGOTIATION;
            case QUOTATION -> next == NEGOTIATION || next == WON;
            case NEGOTIATION -> next == WON;
            default -> false;
        };
        if (!allowed) {
            throw invalid("Leads cannot move from " + name() + " to " + next.name());
        }
    }

    private static RetailflowException invalid(String message) {
        return new RetailflowException(ErrorCodes.INVALID_STATUS, message, HttpStatus.CONFLICT.value());
    }
}
