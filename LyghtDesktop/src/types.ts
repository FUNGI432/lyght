export interface FoundDevice {
  name: string;
  address: string;
}

export interface Effect {
  name: string;
  code: number;
}

export interface EffectGroup {
  label: string;
  items: Effect[];
}

export interface PinOption {
  label: string;
  pins: [number, number, number];
}

export interface EffectCatalog {
  groups: EffectGroup[];
  pin_sequences: PinOption[];
}

export type ConnectionStatus = "disconnected" | "connecting" | "connected";
