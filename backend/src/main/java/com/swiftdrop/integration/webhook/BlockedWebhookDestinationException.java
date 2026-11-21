package com.swiftdrop.integration.webhook;

import java.io.IOException;

/** Permanent policy rejection, distinct from a transient network failure. */
public class BlockedWebhookDestinationException extends IOException {
    public BlockedWebhookDestinationException() { super("DESTINATION_BLOCKED"); }
}
