import { Injectable } from '@angular/core';
import {HttpClient, HttpResponse} from '@angular/common/http';
import {Observable, of, ReplaySubject, Subject} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {Router} from '@angular/router';
import {AuthInfo} from './auth.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly authInfoSubject: Subject<AuthInfo | null>;
  private isInitial = true;

  constructor(private readonly http: HttpClient, private readonly router: Router) {
      this.authInfoSubject = new ReplaySubject<AuthInfo | null>(1);
  }

  authInfo(forceLookup: boolean = true): Observable<AuthInfo | null> {
      if (forceLookup || this.isInitial) {
          this.isInitial = false;
          this.http.get<AuthInfo>('/api/authinfo', {observe: 'response'})
              .pipe(
                  map((response) => response.body),
                  catchError((err) => of(null))
              )
              .subscribe((resp) => this.authInfoSubject.next(resp));
      }

      return this.authInfoSubject.asObservable();
  }

  logout(navigateTo: string | null = '/'): void {
      this.http.post('/logout', null, {observe: 'response'})
          .pipe(
              // 2xx codes -> logout success, 403 -> was already logged out
              map((resp: HttpResponse<any>) => resp.status >= 200 && resp.status < 300),
              catchError((err) => of(err.status == 401 || err.status == 403))
          )
          .subscribe((resp) => {
              if (resp) {
                  this.authInfoSubject.next(null);

                  if (navigateTo != null) {
                      this.router.navigateByUrl(navigateTo);
                  }
              }
          });
  }
}
