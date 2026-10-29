#ifdef GLSLES

#ifdef FRAGMENT_SHADER
/* Only the fragment shader has no default float precision.
 * RMXP-Android-Ports: highp, because mediump (16-bit on Adreno/Mali) isn't
 * precise enough for texture coordinates in big tileset atlases and draws
 * 1-pixel lines from the neighbouring tile. */
precision highp float;
#endif

#else

/* Desktop GLSL doesn't know about these */
#define highp
#define mediump
#define lowp

#endif
