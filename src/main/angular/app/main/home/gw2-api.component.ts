import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {AuthInfo, Gw2ApiToken} from '../../auth.model';
import {Gw2ApiService} from './gw2-api.service';
import {faTrashAlt} from '@fortawesome/free-solid-svg-icons';
import {Gw2ApiPermission} from './gw2.model';
import {ToastService} from '../../toast/toast.service';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {catchError, map} from 'rxjs/operators';
import {of} from 'rxjs';


interface QueryParam {
  name: string;
  value: string;
  disabled: boolean;
}

type Gw2AccountIdAndToken = {gw2AccountId: string, gw2ApiToken: Gw2ApiToken};


@Component({
  selector: 'app-gw2-api',
  templateUrl: './gw2-api.component.html'
})
export class Gw2ApiComponent implements OnInit, OnChanges {

  faTrashAlt = faTrashAlt;
  gw2ApiPermissions: Gw2ApiPermission[] = Object.values(Gw2ApiPermission);

  @Input('authInfo') authInfo!: AuthInfo;

  readonly authorizedGw2ApiPermissions = new Set<Gw2ApiPermission>();
  validApiTokens: Gw2AccountIdAndToken[] = [];
  invalidApiTokens: Gw2AccountIdAndToken[] = [];

  isLoading = false;
  selectedGw2AccountId = '';
  gw2ApiVersion = 'v2';
  gw2ApiUrl = '';
  queryParams: QueryParam[] = [];

  response: string | null = null;

  constructor(private readonly gw2ApiService: Gw2ApiService, private readonly toastService: ToastService, private readonly httpClient: HttpClient) { }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.authInfo) {
      const authInfo = <AuthInfo> changes.authInfo.currentValue;

      this.authorizedGw2ApiPermissions.clear();

      for (let gw2ApiPermission of authInfo.gw2ApiPermissions) {
        this.authorizedGw2ApiPermissions.add(gw2ApiPermission);
      }

      let validApiTokens: Gw2AccountIdAndToken[] = [];
      let invalidApiTokens: Gw2AccountIdAndToken[] = [];
      let selectedGw2AccountIdIsPresent = false;

      for (let [gw2AccountId, gw2ApiToken] of Object.entries(authInfo.gw2ApiTokens)) {
        if (gw2ApiToken.error) {
          invalidApiTokens.push({gw2AccountId: gw2AccountId, gw2ApiToken: gw2ApiToken});
        } else {
          validApiTokens.push({gw2AccountId: gw2AccountId, gw2ApiToken: gw2ApiToken});

          if (gw2AccountId == this.selectedGw2AccountId) {
            selectedGw2AccountIdIsPresent = true;
          }
        }
      }

      this.validApiTokens = validApiTokens.sort((a, b) => a.gw2ApiToken.name.localeCompare(b.gw2ApiToken.name));
      this.invalidApiTokens = invalidApiTokens.sort((a, b) => a.gw2ApiToken.name.localeCompare(b.gw2ApiToken.name));

      if (!selectedGw2AccountIdIsPresent) {
        this.selectedGw2AccountId = '';
      }

      this.toastService.show('AuthInfo updated', 'The AuthInfo has been updated (Token refresh)');
    }
  }

  onAddQueryParamClick(): void {
    this.queryParams.push({name: '', value: '', disabled: false});
  }

  onRemoveQueryParamClick(index: number): void {
    const copy: QueryParam[] = [];

    for (let i = 0; i < this.queryParams.length; i++) {
      if (i != index) {
        copy.push(this.queryParams[i]);
      }
    }

    this.queryParams = copy;
  }

  onRequestApiClick(): void {
    this.isLoading = true;

    const url = this.gw2ApiVersion + '/' + this.gw2ApiUrl.trim().split('/').map(encodeURIComponent).join('/');
    const params: {[K in string]: any} = {};

    for (let queryParam of this.queryParams) {
      if (queryParam.name != '') {
        params[queryParam.name] = queryParam.value;
      }
    }

    const gw2ApiToken = this.authInfo.gw2ApiTokens[this.selectedGw2AccountId];
    let gw2ApiTokenValue;

    if (gw2ApiToken == undefined) {
      gw2ApiTokenValue = undefined;
      this.toastService.show('GW2-API-Request', `Requesting GW2-API at URL ${url}`);
    } else {
      gw2ApiTokenValue = gw2ApiToken.token;
      this.toastService.show('GW2-API-Request', `Requesting GW2-API at URL '${url}' with API-Token '${gw2ApiToken.name}'`);
    }

    this.gw2ApiService.getFromGw2Api(url, params, gw2ApiTokenValue).subscribe((response) => {
      this.isLoading = false;
      this.response = response;
    });
  }

  onBackgroundRefreshClick(): void {
    this.isLoading = true;

    this.httpClient.post('/api/background-refresh', null, { observe: 'response' })
        .pipe(
            map((resp) => resp.status),
            catchError((e: HttpErrorResponse) => of(e.status))
        )
        .subscribe((r) => {
          if (r == 200) {
            this.toastService.show('Background refresh enabled', 'The background refresh for your authorization has been enabled');
          } else {
            this.toastService.show('Failed to enable background refresh', 'The background refresh could not be enabled');
          }

          this.isLoading = false;
        });
  }
}
