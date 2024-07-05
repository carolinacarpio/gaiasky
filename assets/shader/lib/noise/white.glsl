// #name: White

/**
 * Implimentation of 3D white noise.
 * Based on: https://www.ronja-tutorials.com/post/024-white-noise/
 *
 * @name gln_white
 * @function
 * @param {vec3} p Point to sample noise at.
 */
float gln_white(vec3 p){
  vec3 dotDir = vec3(12.9898, 78.233, 37.719);
  vec3 smallValue = sin(p);
  float random = dot(smallValue, dotDir);
  random = fract(sin(random) * 143758.5453);
  return random;
}

/**
 * Generates Fractional Brownian motion (fBm) from 3D White noise.
 *
 * @name gln_wfbm
 * @function
 * @param {vec3} v               Point to sample fBm at.
 * @param {gln_tFBMOpts} opts    Options for generating fBm Noise.
 * @return {float}               Value of fBm at point "p".
 */
float gln_wfbm(vec3 v, gln_tFBMOpts opts) {
  v += (opts.seed * 100.0);
  float result = 0.0;
  float amplitude = opts.amplitude;
  float frequency = opts.frequency;
  float maximum = amplitude;

  for (int i = 0; i < MAX_FBM_ITERATIONS; i++) {
    if (i >= opts.octaves)
    break;

    vec3 p = v * frequency * opts.scale;

    float noiseVal = gln_white(p);

    if (opts.turbulence && !opts.ridge) {
      result += abs(noiseVal) * amplitude;
    } else if (opts.ridge) {
      noiseVal = pow(1.0 - abs(noiseVal), 2.0);
      result += noiseVal * amplitude;
    }

    frequency *= opts.lacunarity;
    amplitude *= opts.persistence;
    maximum += amplitude;
  }

  return pow(result / maximum, opts.power);
}
