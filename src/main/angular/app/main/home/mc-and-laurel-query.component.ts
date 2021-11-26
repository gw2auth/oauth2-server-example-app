import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {AuthInfo, Gw2ApiToken} from '../../auth.model';
import {Gw2ApiService} from './gw2-api.service';
import {ToastService} from '../../toast/toast.service';
import {faBan, faCheck} from '@fortawesome/free-solid-svg-icons';
import {catchError} from 'rxjs/operators';
import {of} from 'rxjs';


type Gw2AccountIdAndToken = {gw2AccountId: string, gw2ApiToken: Gw2ApiToken};
type McAndLaurelAccountResult = {
  gw2ApiToken: Gw2ApiToken;
  mcs: number;
  laurels: number;
}
type McAndLaurelResult = {
  mcs: number;
  laurels: number;
  accounts: McAndLaurelAccountResult[];
}


@Component({
  selector: 'app-mc-and-laurel-query',
  templateUrl: './mc-and-laurel-query.component.html'
})
export class McAndLaurelQueryComponent implements OnInit, OnChanges {

  faCheck = faCheck;
  faBan = faBan;

  @Input('authInfo') authInfo!: AuthInfo;

  validApiTokens: Gw2AccountIdAndToken[] = [];
  readonly selectedApiTokens = new Set<string>();

  isLoading = false;
  result: McAndLaurelResult | null = null;

  constructor(private readonly gw2ApiService: Gw2ApiService, private readonly toastService: ToastService) { }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.authInfo) {
      const authInfo = <AuthInfo> changes.authInfo.currentValue;

      let validApiTokens: Gw2AccountIdAndToken[] = [];

      for (let [gw2AccountId, gw2ApiToken] of Object.entries(authInfo.gw2ApiTokens)) {
        if (gw2ApiToken.token) {
          validApiTokens.push({gw2AccountId: gw2AccountId, gw2ApiToken: gw2ApiToken});
        }
      }

      this.validApiTokens = validApiTokens.sort((a, b) => a.gw2ApiToken.name.localeCompare(b.gw2ApiToken.name));
    }
  }

  onRequestApiClick(): void {
    if (!this.isLoading) {
      this.isLoading = true;
      let waitingForResponseCount = this.selectedApiTokens.size * 2;

      const result: McAndLaurelResult = {mcs: 0, laurels: 0, accounts: []};
      this.result = result;

      for (let gw2AccountId of this.selectedApiTokens) {
        const gw2ApiToken = this.authInfo.gw2ApiTokens[gw2AccountId];

        if (gw2ApiToken != undefined && gw2ApiToken.token) {
          const gw2AccountResult = {gw2ApiToken: gw2ApiToken, mcs: 0, laurels: 0};
          this.result.accounts.push(gw2AccountResult);

          this.gw2ApiService.getFromGw2Api('v2/account/materials', {}, gw2ApiToken.token)
              .pipe(catchError(() => of(null)))
              .subscribe((response) => {
                if (response != null) {
                  for (let material of response) {
                    if (material.id == 19976) {
                      result.mcs += material.count;
                      gw2AccountResult.mcs = material.count;
                      break;
                    }
                  }
                } else {
                  gw2AccountResult.mcs = Number.NaN;
                }

                waitingForResponseCount--;

                if (waitingForResponseCount < 1) {
                  this.isLoading = false;
                }
              });

          this.gw2ApiService.getFromGw2Api('v2/account/wallet', {}, gw2ApiToken.token)
              .pipe(catchError(() => of(null)))
              .subscribe((response) => {
                if (response != null) {
                  for (let currency of response) {
                    if (currency.id == 3) {
                      result.laurels += currency.value;
                      gw2AccountResult.laurels = currency.value;
                      break;
                    }
                  }
                } else {
                  gw2AccountResult.laurels = Number.NaN;
                }

                waitingForResponseCount--;

                if (waitingForResponseCount < 1) {
                  this.isLoading = false;
                }
              });
        }
      }
    }
  }
}
