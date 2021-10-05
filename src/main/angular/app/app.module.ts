import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import {HttpClientModule} from '@angular/common/http';
import { HomeComponent } from './main/home/home.component';
import { FooterComponent } from './footer/footer.component';
import { MainComponent } from './main/main.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {FormsModule} from '@angular/forms';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ToastComponent } from './toast/toast.component';
import {ToastService} from './toast/toast.service';
import { PrivacyPolicyComponent } from './main/privacy-policy/privacy-policy.component';
import {ButtonLoadableComponent} from './general/button-loadable.component';
import { LegalComponent } from './main/legal/legal.component';
import {NgcCookieConsentConfig, NgcCookieConsentModule} from 'ngx-cookieconsent';
import {AuthService} from './auth.service';
import {HTTP_INTERCEPTOR_PROVIDERS} from './http-inceptors';
import { Gw2ApiComponent } from './main/home/gw2-api.component';
import {Gw2ApiService} from './main/home/gw2-api.service';
import {Gw2ApiPermissionBadgeComponent} from './main/home/gw2-api-permission-badge.component';
import {HeaderComponent} from './main/header/header.component';
import {LoginComponent} from './main/home/login.component';


const cookieConfig: NgcCookieConsentConfig = {
  cookie: {
    domain: ''
  },
  palette: {
    popup: {
      background: '#000'
    },
    button: {
      background: '#f1d600'
    }
  },
  position: 'bottom-right',
  theme: 'edgeless',
  type: 'opt-out',
  layout: 'custom',
  layouts: {'custom': '{{message}}{{compliance}}'},
  elements:{
    message: `
    <span id="cookieconsent:desc" class="cc-message">
      {{message}}<a aria-label="learn more about our privacy policy" tabindex="1" class="cc-link" href="{{privacyPolicyHref}}" target="_blank">{{privacyPolicyText}}</a>
    </span>
    `,
  },
  content:{
    message: `This website uses Cookies and Local Storage to offer you the best possible experience. Find out more in our `,

    privacyPolicyText: 'Privacy Policy',
    privacyPolicyHref: '/privacy-policy'
  }
};


@NgModule({
  declarations: [
    AppComponent,
    HomeComponent,
    HeaderComponent,
    FooterComponent,
    MainComponent,
    ToastComponent,
    PrivacyPolicyComponent,
    ButtonLoadableComponent,
    LegalComponent,
    LoginComponent,
    Gw2ApiComponent,
    Gw2ApiPermissionBadgeComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FontAwesomeModule,
    FormsModule,
    NgbModule,
    NgcCookieConsentModule.forRoot(cookieConfig)
  ],
  providers: [
    HTTP_INTERCEPTOR_PROVIDERS,
    ToastService,
    AuthService,
    Gw2ApiService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
