import {Component, Input, OnChanges, OnInit, SimpleChanges} from '@angular/core';
import {AuthInfo, Gw2ApiToken} from '../../auth.model';
import {Gw2ApiService} from './gw2-api.service';
import {faTrashAlt} from '@fortawesome/free-solid-svg-icons';
import {Gw2ApiPermission} from './gw2.model';


interface QueryParam {
  name: string;
  value: string;
}


@Component({
  selector: 'app-gw2-api',
  templateUrl: './gw2-api.component.html'
})
export class Gw2ApiComponent implements OnInit, OnChanges {

  faTrashAlt = faTrashAlt;
  gw2ApiPermissions: Gw2ApiPermission[] = Object.values(Gw2ApiPermission);

  @Input("authInfo") authInfo!: AuthInfo;

  readonly authorizedGw2ApiPermissions = new Set<Gw2ApiPermission>();
  readonly validApiTokens = new Map<string, Gw2ApiToken>();
  readonly invalidApiTokens = new Map<string, Gw2ApiToken>();

  isLoading = false;
  selectedGw2AccountId = '';
  gw2ApiUrl = '';
  queryParams: QueryParam[] = [];

  response: string | null = null;

  constructor(private readonly gw2ApiService: Gw2ApiService) { }

  ngOnInit(): void {
  }

  ngOnChanges(changes: SimpleChanges): void {
    this.authorizedGw2ApiPermissions.clear();
    this.validApiTokens.clear();
    this.invalidApiTokens.clear();

    for (let gw2ApiPermission of this.authInfo.gw2ApiPermissions) {
      this.authorizedGw2ApiPermissions.add(gw2ApiPermission);
    }

    for (let [gw2AccountId, gw2ApiToken] of Object.entries(this.authInfo.gw2ApiTokens)) {
      if (gw2ApiToken.error) {
        this.invalidApiTokens.set(gw2AccountId, gw2ApiToken);
      } else {
        this.validApiTokens.set(gw2AccountId, gw2ApiToken);
      }
    }
  }

  onAddQueryParamClick(): void {
    this.queryParams.push({name: '', value: ''});
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

    const params: {[K in string]: any} = {};

    for (let queryParam of this.queryParams) {
      if (queryParam.name != '') {
        params[queryParam.name] = queryParam.value;
      }
    }

    this.gw2ApiService.getFromGw2Api(this.selectedGw2AccountId, this.gw2ApiUrl, params).subscribe((response) => {
      this.isLoading = false;
      this.response = response;
    })
  }
}
