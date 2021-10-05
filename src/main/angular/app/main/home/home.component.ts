import {Component, OnDestroy, OnInit} from '@angular/core';
import {Subscription} from 'rxjs';
import {AuthService} from '../../auth.service';
import {faUserShield} from '@fortawesome/free-solid-svg-icons';
import {AuthInfo} from '../../auth.model';


@Component({
  selector: 'app-main-home',
  templateUrl: './home.component.html'
})
export class HomeComponent implements OnInit, OnDestroy {

  faUserShield = faUserShield;

  authInfo: AuthInfo | null = null;
  private subscription = new Subscription();

  constructor(private readonly authService: AuthService) { }

  ngOnInit(): void {
    this.subscription.add(
        this.authService.authInfo().subscribe((authInfo) => this.authInfo = authInfo)
    );
  }

  ngOnDestroy(): void {
    const sub = this.subscription;
    this.subscription = new Subscription();

    sub.unsubscribe();
  }
}
