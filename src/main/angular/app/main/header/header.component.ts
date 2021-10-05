import {Component, OnDestroy, OnInit} from '@angular/core';
import {AuthService} from '../../auth.service';
import {Subscription} from 'rxjs';
import {NgbCollapse} from '@ng-bootstrap/ng-bootstrap';


@Component({
  selector: 'app-main-header',
  templateUrl: './header.component.html'
})
export class HeaderComponent implements OnInit, OnDestroy {

  isAuthenticated: boolean = false;
  private subscription = new Subscription();

  constructor(private readonly authService: AuthService) {
  }

  ngOnInit(): void {
    this.subscription.add(
        this.authService.authInfo().subscribe((authInfo) => this.isAuthenticated = authInfo != null)
    );
  }

  ngOnDestroy(): void {
    const sub = this.subscription;
    this.subscription = new Subscription();

    sub.unsubscribe();
  }

  toggleIfShown(collapse: NgbCollapse): void {
    if (!collapse.collapsed) {
      collapse.toggle();
    }
  }

  onLogoutClick(): void {
    this.authService.logout();
  }
}
