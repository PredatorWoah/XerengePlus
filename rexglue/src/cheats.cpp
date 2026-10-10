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

#include <rex/logging.h>

#include "cheats.h"
#include "guest_memory.h"

namespace {

struct Patch {
  uint32_t address;
  uint32_t size;
  uint32_t value;
};

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

}  // namespace

namespace xerenge {

void ApplyCheats(uint8_t* base) {
  static const std::vector<Patch> patches = LoadPatches();
  for (const Patch& patch : patches) {
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
