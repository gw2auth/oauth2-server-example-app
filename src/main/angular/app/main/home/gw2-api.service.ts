import {Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse, HttpResponse} from '@angular/common/http';
import {Observable, of} from 'rxjs';
import {catchError, map} from 'rxjs/operators';

@Injectable()
export class Gw2ApiService {

    constructor(private readonly httpClient: HttpClient) {}

    getFromGw2Api(path: string, params: {[K in string]: any}, gw2ApiToken?: string): Observable<any> {
        const requestURL = 'https://api.guildwars2.com/' + path;

        if (gw2ApiToken != undefined) {
            params['access_token'] = gw2ApiToken;
        }

        return this.httpClient.get(requestURL, { params: params });
    }

    getFromGw2ApiRaw(path: string, params: {[K in string]: any}, gw2ApiToken?: string): Observable<string> {
        const requestURL = 'https://api.guildwars2.com/' + path;

        if (gw2ApiToken != undefined) {
            params['access_token'] = gw2ApiToken;
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
    }
}