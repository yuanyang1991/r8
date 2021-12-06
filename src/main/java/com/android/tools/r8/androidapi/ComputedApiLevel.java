// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.structural.Equatable;
import java.util.Objects;

/**
 * The ComputedApiLevel encodes a lattice over AndroidApiLevel with a bottom (NotSet) and a top
 * (Unknown).
 */
public interface ComputedApiLevel extends Equatable<ComputedApiLevel> {

  static NotSetApiLevel notSet() {
    return NotSetApiLevel.INSTANCE;
  }

  static UnknownApiLevel unknown() {
    return UnknownApiLevel.INSTANCE;
  }

  static KnownApiLevel platform() {
    return KnownApiLevel.PLATFORM_INSTANCE;
  }

  default boolean isNotSetApiLevel() {
    return false;
  }

  default boolean isUnknownApiLevel() {
    return false;
  }

  default ComputedApiLevel max(ComputedApiLevel other) {
    return isGreaterThanOrEqualTo(other) ? this : other;
  }

  default boolean isGreaterThan(ComputedApiLevel other) {
    assert !isNotSetApiLevel() && !other.isNotSetApiLevel()
        : "Cannot compute relationship for not set";
    if (other.isUnknownApiLevel()) {
      return false;
    }
    if (isUnknownApiLevel()) {
      return true;
    }
    assert isKnownApiLevel() && other.isKnownApiLevel();
    return asKnownApiLevel().getApiLevel().isGreaterThan(other.asKnownApiLevel().getApiLevel());
  }

  default boolean isGreaterThanOrEqualTo(ComputedApiLevel other) {
    assert !isNotSetApiLevel() && !other.isNotSetApiLevel()
        : "Cannot compute relationship for not set";
    return other.equals(this) || isGreaterThan(other);
  }

  default boolean isKnownApiLevel() {
    return false;
  }

  default KnownApiLevel asKnownApiLevel() {
    return null;
  }

  @Override
  default boolean isEqualTo(ComputedApiLevel other) {
    return this.equals(other);
  }

  class NotSetApiLevel implements ComputedApiLevel {

    private static final NotSetApiLevel INSTANCE = new NotSetApiLevel();

    private NotSetApiLevel() {}

    @Override
    public boolean isNotSetApiLevel() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  class UnknownApiLevel implements ComputedApiLevel {

    private static final UnknownApiLevel INSTANCE = new UnknownApiLevel();

    private UnknownApiLevel() {}

    @Override
    public boolean isUnknownApiLevel() {
      return true;
    }

    @Override
    public String toString() {
      return "UNKNOWN";
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  class KnownApiLevel implements ComputedApiLevel {

    private static final KnownApiLevel PLATFORM_INSTANCE =
        new KnownApiLevel(AndroidApiLevel.ANDROID_PLATFORM);

    private final AndroidApiLevel apiLevel;

    KnownApiLevel(AndroidApiLevel apiLevel) {
      this.apiLevel = apiLevel;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }

    @Override
    public boolean isKnownApiLevel() {
      return true;
    }

    @Override
    public KnownApiLevel asKnownApiLevel() {
      return this;
    }

    @Override
    public String toString() {
      return apiLevel.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KnownApiLevel)) {
        return false;
      }
      KnownApiLevel that = (KnownApiLevel) o;
      return apiLevel == that.apiLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(apiLevel);
    }
  }
}
