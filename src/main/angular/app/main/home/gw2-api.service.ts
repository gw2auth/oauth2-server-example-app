import {Injectable} from '@angular/core';
import {AuthService} from '../../auth.service';
import {HttpClient, HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';

@Injectable()
export class Gw2ApiService {

    constructor(private readonly authService: AuthService, private readonly httpClient: HttpClient) {}

    getFromGw2Api(gw2AccountId: string, path: string, params: {[K in string]: any} = {}): Observable<string> {
        const requestURL = 'https://api.guildwars2.com/' + path;

        return this.authService.authInfo(false)
            .pipe(switchMap((authInfo) => {
                if (authInfo == null || authInfo.expiresAt.getTime() < Date.now()) {
                    return this.authService.authInfo(true);
                }

                return of(authInfo);
            }))
            .pipe(switchMap((authInfo) => {
                if (authInfo != null) {
                    const gw2ApiToken = authInfo.gw2ApiTokens[gw2AccountId];
                    if (gw2ApiToken != undefined) {
                        params['access_token'] = gw2ApiToken.token;
                    }
                }

                return this.httpClient.get(requestURL, { params: params, observe: 'response', responseType: 'text' } )
                    .pipe(catchError((response: HttpErrorResponse) => of(response)))
                    .pipe(map((response) => {
                        let text = `Status: ${response.status}\n\n`;
                        text += 'Headers:\n';

                        for (let header of response.headers.keys()) {
                            const headerValues = response.headers.getAll(header);
                            if (headerValues != undefined) {
                                text += `${header}: ${headerValues.join('; ')}\n`;
                            }
                        }

                        text += '\nBody:\n';

                        if ((<HttpResponse<string>> response).body) {
                            text += (<HttpResponse<string>> response).body;
                        } else {
                            text += (<HttpErrorResponse> response).error;
                        }

                        return text;
                    }));
            }));
    }
}