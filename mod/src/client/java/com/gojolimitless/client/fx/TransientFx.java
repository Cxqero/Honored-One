package com.gojolimitless.client.fx;

/** A short-lived effect ticked at 20 Hz and drawn every frame by the FX host. */
public interface TransientFx {
    /** @return false when finished */
    boolean tick();

    void render(FxContext ctx);
}
