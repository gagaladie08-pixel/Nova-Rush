import { DEMO_LEAD_IN, demoDef, demoDuration, flattenDemo, type DemoDef } from './songs';

/** Synthétiseur hors ligne des démos (Web Audio, OfflineAudioContext). */

const hz = (m: number) => 440 * Math.pow(2, (m - 69) / 12);

function noiseBuffer(ctx: BaseAudioContext): AudioBuffer {
  const b = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
  const d = b.getChannelData(0);
  let s = 12345;
  for (let i = 0; i < d.length; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    d[i] = (s / 0x7fffffff) * 2 - 1;
  }
  return b;
}

function impulse(ctx: BaseAudioContext, seconds: number, decay: number): AudioBuffer {
  const len = Math.floor(ctx.sampleRate * seconds);
  const b = ctx.createBuffer(2, len, ctx.sampleRate);
  for (let c = 0; c < 2; c++) {
    const d = b.getChannelData(c);
    let s = 777 + c * 31;
    for (let i = 0; i < len; i++) {
      s = (s * 1103515245 + 12345) & 0x7fffffff;
      d[i] = ((s / 0x7fffffff) * 2 - 1) * Math.pow(1 - i / len, decay);
    }
  }
  return b;
}

class Synth {
  noise: AudioBuffer;
  constructor(public ctx: OfflineAudioContext, public dry: AudioNode, public wet: AudioNode) {
    this.noise = noiseBuffer(ctx);
  }

  private env(g: GainNode, t: number, peak: number, attack: number, decay: number) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + attack + decay);
  }

  kick(t: number, vel = 1) {
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.frequency.setValueAtTime(160, t);
    o.frequency.exponentialRampToValueAtTime(45, t + 0.12);
    this.env(g, t, 0.9 * vel, 0.003, 0.32);
    o.connect(g).connect(this.dry);
    o.start(t);
    o.stop(t + 0.4);
  }

  noiseHit(t: number, opts: { type: BiquadFilterType; freq: number; q?: number; peak: number; decay: number; wet?: number }) {
    const src = this.ctx.createBufferSource();
    src.buffer = this.noise;
    const f = this.ctx.createBiquadFilter();
    f.type = opts.type;
    f.frequency.value = opts.freq;
    f.Q.value = opts.q ?? 0.7;
    const g = this.ctx.createGain();
    this.env(g, t, opts.peak, 0.002, opts.decay);
    src.connect(f).connect(g).connect(this.dry);
    if (opts.wet) {
      const w = this.ctx.createGain();
      w.gain.value = opts.wet;
      g.connect(w).connect(this.wet);
    }
    src.start(t, Math.random() * 0.5);
    src.stop(t + opts.decay + 0.05);
  }

  snare(t: number, vel = 1) {
    this.noiseHit(t, { type: 'highpass', freq: 1200, peak: 0.35 * vel, decay: 0.18, wet: 0.3 });
    const o = this.ctx.createOscillator();
    o.type = 'triangle';
    o.frequency.setValueAtTime(220, t);
    o.frequency.exponentialRampToValueAtTime(150, t + 0.08);
    const g = this.ctx.createGain();
    this.env(g, t, 0.3 * vel, 0.002, 0.1);
    o.connect(g).connect(this.dry);
    o.start(t);
    o.stop(t + 0.15);
  }

  clap(t: number, vel = 1) {
    for (let i = 0; i < 3; i++) {
      this.noiseHit(t + i * 0.011, { type: 'bandpass', freq: 1400, q: 1.2, peak: 0.45 * vel, decay: i === 2 ? 0.16 : 0.02, wet: 0.4 });
    }
  }

  hat(t: number, vel = 1, open = false) {
    this.noiseHit(t, { type: 'highpass', freq: 7500, peak: 0.16 * vel, decay: open ? 0.22 : 0.04 });
  }

  shaker(t: number, vel = 1) {
    this.noiseHit(t, { type: 'bandpass', freq: 6000, q: 1.5, peak: 0.12 * vel, decay: 0.05 });
  }

  rim(t: number, vel = 1) {
    const o = this.ctx.createOscillator();
    o.type = 'square';
    o.frequency.value = 1700;
    const f = this.ctx.createBiquadFilter();
    f.type = 'bandpass';
    f.frequency.value = 1800;
    const g = this.ctx.createGain();
    this.env(g, t, 0.18 * vel, 0.001, 0.035);
    o.connect(f).connect(g).connect(this.dry);
    o.start(t);
    o.stop(t + 0.06);
  }

  crash(t: number, vel = 1) {
    this.noiseHit(t, { type: 'highpass', freq: 4500, peak: 0.18 * vel, decay: 1.1, wet: 0.5 });
  }

  bass(t: number, midi: number, dur: number, kind: 'saw' | 'sine' | 'reese' = 'saw', vel = 1) {
    const g = this.ctx.createGain();
    const f = this.ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.setValueAtTime(kind === 'sine' ? 1200 : 900, t);
    f.frequency.exponentialRampToValueAtTime(kind === 'reese' ? 500 : 280, t + Math.max(0.1, dur));
    f.Q.value = kind === 'reese' ? 4 : 2;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(0.32 * vel, t + 0.01);
    g.gain.setValueAtTime(0.32 * vel, t + Math.max(0.02, dur - 0.04));
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.05);
    const detunes = kind === 'reese' ? [-12, 12] : [0];
    for (const d of detunes) {
      const o = this.ctx.createOscillator();
      o.type = kind === 'sine' ? 'sine' : 'sawtooth';
      o.frequency.value = hz(midi);
      o.detune.value = d;
      o.connect(f);
      o.start(t);
      o.stop(t + dur + 0.1);
    }
    f.connect(g).connect(this.dry);
  }

  pad(t: number, chord: number[], dur: number, vel = 1) {
    const g = this.ctx.createGain();
    const f = this.ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = 1400;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(0.05 * vel, t + Math.min(0.6, dur / 3));
    g.gain.setValueAtTime(0.05 * vel, t + dur - 0.3);
    g.gain.linearRampToValueAtTime(0.0001, t + dur);
    for (const m of chord) {
      for (const d of [-8, 8]) {
        const o = this.ctx.createOscillator();
        o.type = 'sawtooth';
        o.frequency.value = hz(m + 12);
        o.detune.value = d;
        o.connect(f);
        o.start(t);
        o.stop(t + dur + 0.05);
      }
    }
    f.connect(g);
    g.connect(this.dry);
    const w = this.ctx.createGain();
    w.gain.value = 0.8;
    g.connect(w).connect(this.wet);
  }

  lead(t: number, midi: number, dur: number, kind: DemoDef['lead'], chord?: number[]) {
    const ctx = this.ctx;
    const g = ctx.createGain();
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    const out = ctx.createGain();
    out.gain.value = 1;
    let peak = 0.16;
    let release = 0.12;
    const notes = [midi];
    if (kind === 'stab' && chord) notes.push(...chord.map((c) => c + 12).filter((c) => c < midi));
    const types: OscillatorType[] = [];
    switch (kind) {
      case 'saw':
        types.push('sawtooth', 'sawtooth');
        f.frequency.value = 2600;
        break;
      case 'square':
        types.push('square', 'sawtooth');
        f.frequency.value = 3400;
        peak = 0.12;
        break;
      case 'stab':
        types.push('sawtooth');
        f.frequency.setValueAtTime(4000, t);
        f.frequency.exponentialRampToValueAtTime(900, t + 0.25);
        peak = 0.12;
        break;
      case 'pluck':
        types.push('triangle', 'square');
        f.frequency.setValueAtTime(3500, t);
        f.frequency.exponentialRampToValueAtTime(700, t + 0.3);
        peak = 0.14;
        release = 0.2;
        break;
      case 'vibes':
        types.push('sine', 'sine');
        f.frequency.value = 6000;
        peak = 0.22;
        release = 0.5;
        break;
    }
    const sustain = kind === 'pluck' || kind === 'vibes' ? 0.35 : 0.8;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + 0.008);
    g.gain.exponentialRampToValueAtTime(peak * sustain, t + 0.12);
    g.gain.setValueAtTime(peak * sustain, t + Math.max(0.13, dur - 0.02));
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur + release);
    for (const n of notes) {
      types.forEach((type, i) => {
        const o = ctx.createOscillator();
        o.type = type;
        if (kind === 'vibes' && i === 1) {
          o.frequency.value = hz(n) * 4; // partiel métallique
          const pg = ctx.createGain();
          pg.gain.setValueAtTime(0.25, t);
          pg.gain.exponentialRampToValueAtTime(0.001, t + 0.3);
          o.connect(pg).connect(f);
        } else {
          o.frequency.value = hz(n);
          o.detune.value = i === 0 ? -6 : 6;
          o.connect(f);
        }
        o.start(t);
        o.stop(t + dur + release + 0.05);
      });
    }
    f.connect(g).connect(out);
    out.connect(this.dry);
    const w = ctx.createGain();
    w.gain.value = 0.35;
    out.connect(w).connect(this.wet);
  }
}

function drums(s: Synth, def: DemoDef, t0: number, beat: number, mode: string, barIdx: number) {
  if (mode === 'none') return;
  const at = (b: number) => t0 + b * beat;
  const light = mode === 'light';
  const build = mode === 'build';
  switch (def.drumStyle) {
    case 'synthwave':
      for (let b = 0; b < 4; b++) {
        if (b % 2 === 0 || !light) s.kick(at(b), b % 2 === 0 ? 1 : 0.5);
        if (b % 2 === 1 && !light) s.snare(at(b));
        s.hat(at(b + 0.5), 0.7);
        if (!light) s.hat(at(b), 0.4);
      }
      break;
    case 'afro':
      s.kick(at(0));
      if (!light) {
        s.kick(at(1.5), 0.7);
        s.kick(at(2), 0.9);
        s.kick(at(3.5), 0.6);
        s.snare(at(1), 0.5);
        s.snare(at(3), 0.6);
      }
      for (let i = 0; i < 16; i++) s.shaker(at(i / 4), i % 4 === 2 ? 1 : 0.55);
      for (const b of [0, 0.75, 1.5, 2.5, 3]) s.rim(at(b), light ? 0.6 : 1);
      break;
    case 'ethio':
      s.kick(at(0));
      s.kick(at(2.5), 0.8);
      if (!light) {
        s.snare(at(1), 0.7);
        s.snare(at(3), 0.8);
        s.kick(at(3.5), 0.5);
      }
      for (let b = 0; b < 4; b++) {
        s.hat(at(b), 0.6);
        s.hat(at(b + 2 / 3), 0.35);
      }
      break;
    case 'vogue':
      for (let b = 0; b < 4; b++) {
        if (!build || b % 2 === 0) s.kick(at(b));
        s.hat(at(b + 0.5), build ? 0.5 : 0.8, !build && b === 3);
      }
      if (!build) {
        s.clap(at(1));
        s.clap(at(3));
        // Le « Ha » du ballroom : coup de clap/crash en contretemps
        s.clap(at(3.5), 0.8);
        if (barIdx % 4 === 0) s.crash(at(0), 0.8);
      }
      break;
    case 'dnb':
      if (build) {
        for (let i = 0; i < 8; i++) s.hat(at(i / 2), 0.4 + i * 0.05);
        if (barIdx % 2 === 1) s.snare(at(3), 0.6);
        break;
      }
      s.kick(at(0));
      if (!light) {
        s.kick(at(2.5), 0.9);
        s.snare(at(1));
        s.snare(at(3));
        for (let i = 0; i < 8; i++) s.hat(at(i / 2), i % 2 ? 0.8 : 0.45);
        if (barIdx % 8 === 0) s.crash(at(0), 0.9);
      } else {
        s.hat(at(1), 0.5);
        s.hat(at(3), 0.5);
      }
      break;
  }
}

export async function renderDemo(id: string, sampleRate = 44100): Promise<AudioBuffer> {
  const def = demoDef(id);
  if (!def) throw new Error('Démo inconnue');
  const duration = demoDuration(def);
  const ctx = new OfflineAudioContext(2, Math.ceil(duration * sampleRate), sampleRate);
  const master = ctx.createGain();
  master.gain.value = 0.85;
  const comp = ctx.createDynamicsCompressor();
  comp.threshold.value = -14;
  comp.ratio.value = 4;
  comp.attack.value = 0.005;
  comp.release.value = 0.2;
  master.connect(comp).connect(ctx.destination);
  const verb = ctx.createConvolver();
  verb.buffer = impulse(ctx, 1.4, 3);
  const verbGain = ctx.createGain();
  verbGain.gain.value = 0.35;
  verb.connect(verbGain).connect(master);
  const s = new Synth(ctx, master, verb);

  const beat = 60 / def.bpm;
  const bars = flattenDemo(def);
  const bassKind = def.drumStyle === 'dnb' ? 'reese' : def.drumStyle === 'ethio' ? 'sine' : 'saw';
  const buses: (GainNode[] | null)[] = [];
  const scheduleBar = (i: number) => {
    const bar = bars[i];
    // Bus propre à la mesure, débranché une fois ses sons terminés.
    const dry = ctx.createGain();
    const wet = ctx.createGain();
    dry.connect(master);
    wet.connect(verb);
    s.dry = dry;
    s.wet = wet;
    buses[i] = [dry, wet];
    const old = buses[i - 3];
    if (old) {
      old.forEach((n) => n.disconnect());
      buses[i - 3] = null;
    }
    const t0 = DEMO_LEAD_IN + i * 4 * beat;
    drums(s, def, t0, beat, bar.drums, i);
    s.pad(t0, bar.chord, 4 * beat, bar.drums === 'full' ? 1 : 1.3);
    const root = bar.chord[0] - 12 < 33 ? bar.chord[0] : bar.chord[0] - 12;
    if (bar.drums !== 'none') {
      const pattern: [number, number, number][] =
        def.drumStyle === 'afro'
          ? [[0, 0, 0.4], [0.75, 0, 0.25], [1.5, 7, 0.4], [2.5, 0, 0.4], [3, 12, 0.25], [3.5, 7, 0.4]]
          : def.drumStyle === 'ethio'
            ? [[0, 0, 0.9], [1, 7, 0.4], [1.5, 8, 0.4], [2, 7, 0.9], [3, 0, 0.4], [3.5, -1, 0.4]]
            : def.drumStyle === 'dnb'
              ? [[0, 0, 1.4], [2.5, 0, 1.2]]
              : def.drumStyle === 'vogue'
                ? [[0.5, 0, 0.35], [1.5, 0, 0.35], [2.5, 0, 0.35], [3.5, 12, 0.35]]
                : [[0, 0, 0.4], [0.5, 0, 0.4], [1, 0, 0.4], [1.5, 12, 0.4], [2, 0, 0.4], [2.5, 0, 0.4], [3, 0, 0.4], [3.5, 12, 0.4]];
      const vel = bar.drums === 'light' || bar.drums === 'build' ? 0.6 : 1;
      for (const [pos, interval, len] of pattern) s.bass(t0 + pos * beat, root + interval, len * beat, bassKind, vel);
    }
    for (const [pos, midi, len] of bar.notes) {
      s.lead(t0 + pos * beat, midi, Math.max(0.08, len * beat - 0.02), def.lead, bar.chord);
    }
  };

  // Les nœuds sont créés mesure par mesure (suspend/resume) : un graphe
  // contenant tous les nœuds dès le départ rendrait le calcul très lent.
  const quantum = 128 / sampleRate;
  scheduleBar(0);
  for (let i = 1; i < bars.length; i++) {
    const at = Math.floor((DEMO_LEAD_IN + i * 4 * beat - 0.1) / quantum) * quantum;
    void ctx.suspend(at).then(() => {
      scheduleBar(i);
      void ctx.resume();
    });
  }
  return ctx.startRendering();
}
