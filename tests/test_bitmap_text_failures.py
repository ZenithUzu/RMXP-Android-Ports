"""Compile the actual SDL surface guards with fault-injecting SDL stubs.

This does not replace an Android/SDL integration build or the Relict repro.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


def block(source, marker):
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


@unittest.skipUnless(shutil.which("c++"), "C++ compiler required")
class BitmapTextFailures(unittest.TestCase):
    def test_sdl_failures(self):
        root = Path(__file__).resolve().parents[1]
        path = root / "android/app/jni/mkxp-z/src/display/bitmap.cpp"
        if not path.exists():
            path = root / "src/display/bitmap.cpp"
        source = path.read_text()
        format_guard = block(source, "static void ensureFormat")
        shadow = block(source, "static void applyShadow")
        text_guard = block(source, "if (!txtSurf)")
        program = r'''
#include <cassert>
#include <cstdint>
#include <stdexcept>
#include <string>
using Uint32 = uint32_t;
struct Exception : std::runtime_error {
    enum { SDLError };
    Exception(int, const char*, const char *msg) : std::runtime_error(msg) {}
};
struct SDL_PixelFormat {
    Uint32 format = 1;
    int BitsPerPixel = 32;
    Uint32 Rmask = 0, Gmask = 0, Bmask = 0, Amask = 255;
    int Ashift = 0;
};
struct SDL_Surface {
    SDL_PixelFormat *format;
    void *pixels = nullptr;
    int w = 1, h = 1, pitch = 4;
};
struct SDL_Color { uint8_t r, g, b, a; };
struct TTF_Font {};
int freed = 0;
bool conversionFails = false;
bool measurementFails = false;
int measuredWidth = 1;
const char *SDL_GetError() { return "injected SDL failure"; }
const char *TTF_GetError() { return "injected render failure"; }
void SDL_FreeSurface(SDL_Surface *p) { if (p) { ++freed; delete p; } }
SDL_PixelFormat convertedFormat;
SDL_Surface *SDL_ConvertSurfaceFormat(SDL_Surface*, Uint32 format, int) {
    if (conversionFails) return nullptr;
    convertedFormat.format = format;
    return new SDL_Surface{&convertedFormat};
}
SDL_Surface *SDL_CreateRGBSurface(int, int, int, int, Uint32, Uint32, Uint32, Uint32) {
    return nullptr;
}
Uint32 SDL_MapRGBA(const SDL_PixelFormat*, uint8_t, uint8_t, uint8_t, uint8_t) { return 0; }
template<class T> T clamp(T v, T lo, T hi) { return v < lo ? lo : v > hi ? hi : v; }
int TTF_SizeUTF8(TTF_Font*, const char*, int *w, int *h) {
    *w = measuredWidth; *h = 10;
    return measurementFails ? -1 : 0;
}
'''
        program += format_guard + "\n" + shadow
        program += "\nvoid checkRender(SDL_Surface *txtSurf, TTF_Font *font, const char *str) {\n"
        program += text_guard + "\n}\n"
        program += r'''
int main() {
    SDL_Surface *s = nullptr;
    try { ensureFormat(s, 2); assert(false); } catch (const Exception&) {}
    assert(freed == 0);
    SDL_PixelFormat originalFormat;
    s = new SDL_Surface{&originalFormat};
    auto *original = s;
    ensureFormat(s, 1);
    assert(s == original && freed == 0);
    ensureFormat(s, 2);
    assert(s != original && s->format->format == 2 && freed == 1);
    SDL_FreeSurface(s);
    s = new SDL_Surface{&originalFormat};
    conversionFails = true;
    try { ensureFormat(s, 2); assert(false); } catch (const Exception &e) {
        assert(std::string(e.what()) == "injected SDL failure");
    }
    assert(s == nullptr && freed == 3);
    s = new SDL_Surface{&originalFormat};
    original = s;
    try { applyShadow(s, originalFormat, SDL_Color{0,0,0,255}); assert(false); }
    catch (const Exception&) {}
    assert(s == original && freed == 3);
    SDL_FreeSurface(s);
    measuredWidth = 0;
    checkRender(nullptr, nullptr, "zero width");
    measuredWidth = 1;
    try { checkRender(nullptr, nullptr, "text"); assert(false); }
    catch (const Exception &e) {
        assert(std::string(e.what()) == "injected render failure");
    }
    measuredWidth = 0;
    measurementFails = true;
    try { checkRender(nullptr, nullptr, "invalid"); assert(false); }
    catch (const Exception&) {}
    s = new SDL_Surface{&originalFormat};
    checkRender(s, nullptr, "ok");
    SDL_FreeSurface(s);
    assert(freed == 5);
}
'''
        with tempfile.TemporaryDirectory() as tmp:
            cpp = Path(tmp) / "test.cpp"
            exe = Path(tmp) / "test"
            cpp.write_text(program)
            subprocess.run(["c++", "-std=c++14", "-Wall", "-Wextra", "-Werror",
                            str(cpp), "-o", str(exe)], check=True)
            subprocess.run([str(exe)], check=True)


if __name__ == "__main__":
    unittest.main()
