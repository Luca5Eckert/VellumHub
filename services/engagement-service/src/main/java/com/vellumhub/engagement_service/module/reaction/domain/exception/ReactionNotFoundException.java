package com.vellumhub.engagement_service.module.reaction.domain.exception;

/** Same not-found response for nonexistent or unauthorized private reactions. */
public class ReactionNotFoundException extends ReactionException {
    public ReactionNotFoundException() {
        super("Reaction resource not found");
    }
}
