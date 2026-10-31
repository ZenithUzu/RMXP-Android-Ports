/* Portémon's adapter for the game's own RPG Maker MV core. No MV code is bundled. */
(function () {
    'use strict';
    var speed = 1, stretch = 0, target = 0, frames = 0, lastReport = 0;
    var scaleX = 1, scaleY = 1;
    window.PortemonMV = {
        configure: function (s, st, fps) {
            speed = Math.max(0, Math.min(10, s)); stretch = Math.max(0, Math.min(100, st)); target = fps;
            if (window.SceneManager) {
                SceneManager._currentTime = performance.now(); SceneManager._accumulator = 0;
            }
            if (window.Graphics && Graphics._canvas) Graphics._updateAllElements();
            if (window.WebAudio && WebAudio._context) {
                var context = WebAudio._context;
                if (speed === 0 && context.state === 'running') context.suspend();
                else if (speed > 0 && context.state === 'suspended') context.resume().catch(function () {});
            }
        },
        key: function (code, down) {
            var event = new KeyboardEvent(down ? 'keydown' : 'keyup', {bubbles: true, cancelable: true});
            Object.defineProperty(event, 'keyCode', {get: function () {return code;}});
            Object.defineProperty(event, 'which', {get: function () {return code;}});
            document.dispatchEvent(event);
            if (down && code === 8) {
                var input = document.activeElement;
                if (input && typeof input.setRangeText === 'function' && input.selectionStart !== null) {
                    input.setRangeText('', Math.max(0, input.selectionStart - (input.selectionStart === input.selectionEnd ? 1 : 0)), input.selectionEnd, 'end');
                    input.dispatchEvent(new Event('input', {bubbles: true}));
                }
            }
        },
        text: function (text) {
            var input = document.activeElement;
            if (input && typeof input.setRangeText === 'function' && input.selectionStart !== null) {
                input.setRangeText(text, input.selectionStart, input.selectionEnd, 'end');
                input.dispatchEvent(new Event('input', {bubbles: true}));
            }
        }
    };
    // Registered before main.js's load handler: plugins have loaded, game boot has not started.
    window.addEventListener('load', function () {
        if (!window.StorageManager || !window.SceneManager || !window.Graphics) {
            console.error('Portémon: núcleo MV incompleto'); return;
        }
        var bridge = window.PortemonAndroid;
        function name(id) {
            if (!Number.isInteger(id) || id < -1 || id > 999999) throw new Error('Invalid MV save slot');
            return (id < 0 ? 'config' : id === 0 ? 'global' : 'file' + id) + '.rpgsave';
        }
        function write(file, data) {
            if (!bridge.save(file, data)) throw new Error('No se pudo guardar ' + file);
        }
        StorageManager.save = function (id, json) {write(name(id), LZString.compressToBase64(json));};
        StorageManager.load = function (id) {return LZString.decompressFromBase64(bridge.load(name(id)));};
        StorageManager.exists = function (id) {return bridge.load(name(id)) !== null;};
        StorageManager.remove = function (id) {bridge.remove(name(id));};
        StorageManager.backup = function (id) {
            var data = bridge.load(name(id)); if (data !== null) write(name(id) + '.bak', data);
        };
        StorageManager.backupExists = function (id) {return bridge.load(name(id) + '.bak') !== null;};
        StorageManager.cleanBackup = function (id) {bridge.remove(name(id) + '.bak');};
        StorageManager.restoreBackup = function (id) {
            var data = bridge.load(name(id) + '.bak');
            if (data !== null) {write(name(id), data); bridge.remove(name(id) + '.bak');}
        };
        // Physical controller events are already handled by the shared Android mapper.
        Input._pollGamepads = function () {};
        var update = SceneManager.update;
        SceneManager.update = function () {
            if (speed === 0) {this.requestUpdate(); return;}
            update.apply(this, arguments);
        };
        var main = SceneManager.updateMain;
        SceneManager.updateMain = function () {
            var delta = this._deltaTime;
            this._deltaTime = 1 / ((target > 0 ? target : 60) * Math.max(1, speed));
            try {return main.apply(this, arguments);} finally {this._deltaTime = delta;}
        };
        var render = Graphics.render;
        Graphics.render = function () {
            var drawn = this._skipCount <= 0;
            var result = render.apply(this, arguments);
            if (drawn) frames++;
            var now = performance.now();
            if (now - lastReport >= 1000) {bridge.frames(frames); lastReport = now;}
            return result;
        };
        var center = Graphics._centerElement;
        Graphics._centerElement = function (element) {
            center.apply(this, arguments);
            if (element.tagName !== 'CANVAS' && element.tagName !== 'VIDEO') return;
            var base = Math.min(window.innerWidth / this._width, window.innerHeight / this._height);
            var t = stretch / 100;
            scaleX = base + (window.innerWidth / this._width - base) * t;
            scaleY = base + (window.innerHeight / this._height - base) * t;
            element.style.width = this._width * scaleX + 'px';
            element.style.height = this._height * scaleY + 'px';
            element.style.imageRendering = 'pixelated';
        };
        Graphics.pageToCanvasX = function (x) {return this._canvas ? Math.round((x - this._canvas.offsetLeft) / scaleX) : 0;};
        Graphics.pageToCanvasY = function (y) {return this._canvas ? Math.round((y - this._canvas.offsetTop) / scaleY) : 0;};
    });
}());
