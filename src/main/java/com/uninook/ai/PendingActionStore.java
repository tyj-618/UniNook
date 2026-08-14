package com.uninook.ai;

import java.util.Optional;

/**
 * Hot storage for confirmation-required drafts. A future durable store can implement this contract.
 */
public interface PendingActionStore {

    void save(PendingAction action);

    Optional<PendingAction> load(Long userId, String actionId);

    Optional<PendingAction> take(Long userId, String actionId);

    void delete(Long userId, String actionId);
}
