package com.npcore.ems.desktop.api;

import com.npcore.ems.desktop.api.Dtos.Me;
import java.util.HashSet;
import java.util.Set;

/** Phiên đăng nhập hiện tại: người dùng + quyền (mã quyền; "MÃ:ALL" = phạm vi toàn bộ). */
public final class Session {

    private final Api api;
    private volatile Me me;
    private volatile Set<String> perms = Set.of();

    public Session(Api api, Me me) {
        this.api = api;
        update(me);
    }

    public void update(Me me) {
        this.me = me;
        this.perms = new HashSet<>(me.permissions());
    }

    public Api api() { return api; }

    public Me me() { return me; }

    public boolean has(String permission) { return perms.contains(permission); }

    public boolean hasAll(String permission) { return perms.contains(permission + ":ALL"); }

    public boolean hasAny(String... permissions) {
        for (String p : permissions) if (has(p)) return true;
        return false;
    }
}
