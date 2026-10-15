// Cheats for the PAL (Europe) disc: memory patches kept in a file the in-game
// menu writes (Android: CheatsMenu.java), read once at start, named by
// XERENGE_CHEATS. One enabled patch a line:
//
//   <name> | <guest address, hex> | <size: 1, 2 or 4> | <value, hex>
//
// The menu restarts the game after a change, so the file is only read here
// once. Each patch is stored again every frame, which holds a value the title
// keeps rewriting (a counter, a timer) where it was put.
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

#include <rex/hook.h>
#include <rex/logging.h>
#include <rex/ppc.h>

#include "cheats.h"
#include "guest_memory.h"

namespace {

struct Patch {
  uint32_t address;
  uint32_t size;
  uint32_t value;
};

// Addresses from here up are not memory: switches for the hooks below.
constexpr uint32_t kVirtualBase = 0xFFFF0000u;
constexpr uint32_t kGoldMedals = 0xFFFF0002u;  // every result recorded as a gold medal

// The profile object (0x8281FA78) and the flag bytes the developer "Unlock All"
// sets. +0x400 and +0x401 also make the game skip recording and saving a result
// (CGt... RecordResult, 0x82101090), so while they are set no race counts.
constexpr uint32_t kProfileFlagRecord = 0x8281FE78u;  // +0x400
constexpr uint32_t kProfileFlagSave = 0x8281FE79u;    // +0x401

std::vector<Patch> LoadPatches() {
  std::vector<Patch> patches;
  const char* path = std::getenv("XERENGE_CHEATS");
  if (path == nullptr || *path == '\0') {
    return patches;
  }
  std::ifstream in(path);
  std::string line;
  while (std::getline(in, line)) {
    if (line.empty() || line[0] == '#') {
      continue;
    }
    // name | address | size | value
    std::string fields[4];
    size_t field = 0;
    for (char c : line) {
      if (c == '|' && field < 3) {
        ++field;
      } else {
        fields[field] += c;
      }
    }
    if (field < 3) {
      continue;
    }
    const uint32_t address = uint32_t(std::strtoul(fields[1].c_str(), nullptr, 16));
    const uint32_t size = uint32_t(std::strtoul(fields[2].c_str(), nullptr, 10));
    const uint32_t value = uint32_t(std::strtoul(fields[3].c_str(), nullptr, 16));
    // Only the title's own memory, and only sizes that make sense.
    if (address < 0x10000 || (size != 1 && size != 2 && size != 4)) {
      continue;
    }
    patches.push_back({address, size, value});
  }
  REXLOG_INFO("cheats: {} patch(es) from {}", patches.size(), path);
  return patches;
}

const std::vector<Patch>& Patches() {
  static const std::vector<Patch> patches = LoadPatches();
  return patches;
}

bool Active(uint32_t address) {
  for (const Patch& patch : Patches()) {
    if (patch.address == address && patch.value != 0) {
      return true;
    }
  }
  return false;
}

}  // namespace

namespace xerenge {

void ApplyCheats(uint8_t* base) {
  for (const Patch& patch : Patches()) {
    if (patch.address >= kVirtualBase) {
      continue;
    }
    uint8_t* target = GuestPointer(base, patch.address);
    switch (patch.size) {
      case 1:
        *target = uint8_t(patch.value);
        break;
      case 2: {
        const uint16_t swapped = __builtin_bswap16(uint16_t(patch.value));
        std::memcpy(target, &swapped, 2);
        break;
      }
      default:
        StoreGuestU32(base, patch.address, patch.value);
        break;
    }
  }
}

}  // namespace xerenge

extern "C" void __imp__sub_82101090(PPCContext& __restrict, uint8_t*);

// Records the result of a finished event in the profile: r3 the profile. With
// the developer unlock flags held on, +0x400 and +0x401 make it do nothing and
// save nothing; they are held off for the call, so the result counts and is
// saved.
REX_HOOK_RAW(sub_82101090) {
  static const bool keep = Active(kProfileFlagRecord) || Active(kProfileFlagSave);
  const uint32_t profile = ctx.r3.u32;
  uint8_t saved[2] = {0, 0};
  if (keep && profile != 0) {
    for (int i = 0; i < 2; ++i) {
      uint8_t* flag = xerenge::GuestPointer(base, profile + 0x400 + i);
      saved[i] = *flag;
      *flag = 0;
    }
  }
  __imp__sub_82101090(ctx, base);
  if (keep && profile != 0) {
    for (int i = 0; i < 2; ++i) {
      *xerenge::GuestPointer(base, profile + 0x400 + i) = saved[i];
    }
  }
}

extern "C" void __imp__sub_821004B0(PPCContext& __restrict, uint8_t*);

// Works the profile's progress out again (r3 the profile): which events are done,
// the stars they add up to, the ranks those open. It runs after a profile loads
// and after every result. Per event the profile keeps a result byte at +0x1C0
// (0 undone, up to 3) and a star tier at +0x2D3 (-1 none, 0 to 4: one to five
// stars) - the events are the ids in the table at +0x4E0, +0x318, counted at
// +0x2B4. With the cheat on every event is made done with its five stars first.
REX_HOOK_RAW(sub_821004B0) {
  static const bool stars = Active(kGoldMedals);
  const uint32_t profile = ctx.r3.u32;
  if (stars && profile != 0) {
    const uint32_t table = xerenge::LoadGuestU32(base, profile + 0x4E0);
    if (table != 0) {
      uint32_t count = xerenge::LoadGuestU32(base, table + 0x2B4);
      if (count > 150) {
        count = 150;
      }
      for (uint32_t i = 0; i < count; ++i) {
        uint8_t* result = xerenge::GuestPointer(base, profile + 0x1C0 + i);
        if (*result < 3) {
          *result = 3;
        }
        *xerenge::GuestPointer(base, profile + 0x2D3 + i) = 4;
      }
    }
  }
  __imp__sub_821004B0(ctx, base);
}
