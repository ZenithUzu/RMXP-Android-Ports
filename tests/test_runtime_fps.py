"""Exercise the real Android frame-rate setter and override transition."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class RuntimeFpsTest(unittest.TestCase):
    def test_restores_latest_game_request(self):
        source = (ROOT / 'android/app/jni/mkxp-z/src/display/graphics.cpp').read_text()
        setter = source.split('void Graphics::setFrameRate(int value) {', 1)[1].split('\ndouble Graphics::averageFrameRate', 1)[0]
        update = source.split('    int forcedFps = rmxpFps;', 1)[1].split('    /* Fast forward:', 1)[0]
        cpp = '''
#include <algorithm>
#include <cassert>
#define __ANDROID__ 1
using std::clamp;
int rmxpFps=0, rmxpSpeed=1;
struct Input { void recalcRepeat(unsigned){} };
struct State { Input i; Input &input(){return i;} } state, *shState=&state;
struct Limiter { int desired=40; void setDesiredFPS(int i){desired=i;} int getDesiredFPS(){return desired;} bool disabled=false; void resetFrameAdjust(){} };
struct Config { bool syncToRefreshrate=false; int fixedFramerate=0; };
struct Data { Config config; };
struct Private { int frameRate=40, gameFrameRate=40, ffSpeed=1; Limiter fpsLimiter; Data d; Data *threadData=&d; };
struct Graphics { Private data, *p=&data; void setFrameRate(int); void update(){ int forcedFps=rmxpFps; UPDATE } };
void Graphics::setFrameRate(int value) { SETTER
int main(){
 Graphics g;
 g.setFrameRate(40); g.update(); assert(g.p->frameRate==40);
 rmxpFps=60; g.update(); assert(g.p->frameRate==60 && g.p->fpsLimiter.desired==60);
 g.setFrameRate(30); assert(g.p->frameRate==60);
 rmxpFps=0; g.update(); assert(g.p->frameRate==30 && g.p->fpsLimiter.desired==30);
 g.setFrameRate(40); g.update(); assert(g.p->frameRate==40);
 rmxpFps=120; g.update(); rmxpSpeed=5; g.update(); assert(g.p->fpsLimiter.desired==600);
 rmxpFps=0; g.update(); assert(g.p->frameRate==40 && g.p->fpsLimiter.desired==200);
 rmxpSpeed=1; g.update(); assert(g.p->fpsLimiter.desired==40);
 // Startup has frameRate=60 but mkxp.json set the limiter to 40. No turbo transition.
 Graphics startup; startup.p->frameRate=60; startup.p->gameFrameRate=60;
 startup.p->fpsLimiter.desired=40; startup.p->threadData->config.fixedFramerate=40;
 rmxpFps=60; startup.update(); assert(startup.p->fpsLimiter.desired==60);
 rmxpFps=120; startup.update(); assert(startup.p->fpsLimiter.desired==120);
 startup.p->fpsLimiter.desired=40; startup.p->fpsLimiter.disabled=true;
 startup.update(); assert(startup.p->fpsLimiter.desired==120 && !startup.p->fpsLimiter.disabled);
 rmxpFps=0; startup.update(); assert(startup.p->fpsLimiter.desired==40);
}
'''.replace('SETTER', setter).replace('UPDATE', update)
        with tempfile.TemporaryDirectory() as tmp:
            source_path = Path(tmp) / 'fps.cpp'
            binary = Path(tmp) / 'fps'
            source_path.write_text(cpp)
            subprocess.run(['c++', '-std=c++17', str(source_path), '-o', str(binary)], check=True, capture_output=True)
            subprocess.run([str(binary)], check=True)


if __name__ == '__main__':
    unittest.main()
