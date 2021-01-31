import { WebPlugin } from '@capacitor/core';
import { BackgroundGeolocationOptions, BackgroundGeolocationPlugin } from './definitions';

export class BackgroundGeolocationWeb extends WebPlugin implements BackgroundGeolocationPlugin {
  constructor() {
    super({
      name: 'BackgroundGeolocation',
      platforms: ['web'],
    });
  }

  public addWatcher(options: BackgroundGeolocationOptions): Promise<string> {
    console.log("Web not implemented", options);
    return Promise.resolve("");
  }

  public removeWatcher(id: {id: string}): Promise<void> {
    console.log("Web not implemented", id);
    return Promise.resolve();
  }

  public openSettings(): Promise<void> {
    return Promise.resolve();
  }

}

const BackgroundGeolocation = new BackgroundGeolocationWeb();

export { BackgroundGeolocation };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(BackgroundGeolocation);
