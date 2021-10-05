import {Component, OnInit} from '@angular/core';
import {NgcCookieConsentService} from "ngx-cookieconsent";


@Component({
  selector: 'app-root',
  templateUrl: './app.component.html'
})
export class AppComponent implements OnInit {

  constructor(private readonly cookieConsentService: NgcCookieConsentService) {

  }

  ngOnInit(): void {
  }
}
