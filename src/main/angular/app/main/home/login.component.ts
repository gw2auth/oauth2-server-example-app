import {Component, OnInit} from '@angular/core';
import {faUserShield} from '@fortawesome/free-solid-svg-icons';
import {Gw2ApiPermission} from './gw2.model';


@Component({
  selector: 'app-login',
  templateUrl: './login.component.html'
})
export class LoginComponent implements OnInit {

  faUserShield = faUserShield;
  gw2ApiPermissions: Gw2ApiPermission[] = Object.values(Gw2ApiPermission);

  requestGw2ApiPermissions = new Set<Gw2ApiPermission>();
  scopeQueryString = '';

  constructor() {
    this.requestGw2ApiPermissions.add(Gw2ApiPermission.ACCOUNT);
  }

  ngOnInit(): void {
  }

  onRequestGw2ApiPermissionClick(gw2ApiPermission: Gw2ApiPermission): void {
    if (this.requestGw2ApiPermissions.has(gw2ApiPermission) && gw2ApiPermission != Gw2ApiPermission.ACCOUNT) {
      this.requestGw2ApiPermissions.delete(gw2ApiPermission);
    } else {
      this.requestGw2ApiPermissions.add(gw2ApiPermission);
    }

    this.scopeQueryString = [...this.requestGw2ApiPermissions.values()].map((v) => 'gw2:' + v).join(' ');
  }
}
