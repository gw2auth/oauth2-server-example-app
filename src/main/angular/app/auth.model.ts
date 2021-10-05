import {Gw2ApiPermission} from './main/home/gw2.model';

export interface Gw2ApiToken {
    name: string;
    token?: string;
    error?: string;
}

export interface AuthInfo {
    sub: string;
    gw2ApiPermissions: Gw2ApiPermission[];
    gw2ApiTokens: {[K in string]: Gw2ApiToken};
    expiresAt: Date;
}