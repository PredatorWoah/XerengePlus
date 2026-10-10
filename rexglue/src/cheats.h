#pragma once

#include <cstdint>

namespace xerenge {

// Writes the enabled cheats into the title's memory (cheats.cpp); cheap, and
// called once a frame so a patched value stays patched.
void ApplyCheats(uint8_t* base);

}  // namespace xerenge
