import { Injectable } from '@angular/core';
import {HttpClient, HttpResponse} from '@angular/common/http';
import {Observable, of, ReplaySubject, Subject, Subscription, timer} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {Router} from '@angular/router';
import {AuthInfo, Gw2ApiToken} from './auth.model';
import {Gw2ApiPermission} from './main/home/gw2.model';


@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly authInfoSubject: Subject<AuthInfo | null>;
  private latestAuthInfo: AuthInfo | null = null;
  private isInitial = true;

  private keepAliveSubscription: Subscription | null = null;

  constructor(private readonly http: HttpClient, private readonly router: Router) {
      this.authInfoSubject = new ReplaySubject<AuthInfo | null>(1);
  }

  authInfo(forceLookup: boolean = true): Observable<AuthInfo | null> {
      if (forceLookup || this.isInitial) {
          this.http.get<AuthInfo>('/api/authinfo', {observe: 'response'})
              .pipe(
                  map((response) => response.body),
                  catchError((err) => of(null))
              )
              .subscribe((resp) => this.next(resp));
      }

      return this.authInfoSubject.asObservable();
  }

  keepAlive(interval: number): Observable<AuthInfo | null> {
      if (this.keepAliveSubscription != null) {
          this.keepAliveSubscription.unsubscribe();
      }

      this.keepAliveSubscription = timer(interval, interval).subscribe(() => this.authInfo(true));

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
                  this.next(null);

                  if (navigateTo != null) {
                      this.router.navigateByUrl(navigateTo);
                  }
              }
          });
  }

  private next(authInfo: AuthInfo | null, force: boolean = false): void {
      if (this.isInitial) {
          this.isInitial = false;
          force = true;
      }

      if (force || !this.nullableAuthInfoEquals(authInfo, this.latestAuthInfo)) {
          this.latestAuthInfo = authInfo;
          this.authInfoSubject.next(authInfo);
      }
  }

  private nullableAuthInfoEquals(a: AuthInfo | null, b: AuthInfo | null): boolean {
      if (a == b) {
          return true;
      } else if (a == null || b == null) {
          return false;
      }

      return this.authInfoEquals(a, b);
  }

  private authInfoEquals(a: AuthInfo, b: AuthInfo): boolean {
      if (a.sub != b.sub) {
          return false;
      } else if (a.expiresAt.getTime() != b.expiresAt.getTime()) {
          return false;
      }

      // compare permissions
      const gw2ApiPermissionsA = new Set<Gw2ApiPermission>();

      for (let gw2ApiPermission of a.gw2ApiPermissions) {
          gw2ApiPermissionsA.add(gw2ApiPermission);
      }

      // remove all permissions that are present in b
      for (let gw2ApiPermission of b.gw2ApiPermissions) {
          if (!gw2ApiPermissionsA.delete(gw2ApiPermission)) {
              return false;
          }
      }

      // if anything is left, theyre not equal
      if (gw2ApiPermissionsA.size > 0) {
          return false;
      }

      // compare tokens
      const gw2ApiTokensA = new Map<string, Gw2ApiToken>();

      for (let [gw2AccountId, gw2ApiToken] of Object.entries(a.gw2ApiTokens)) {
          gw2ApiTokensA.set(gw2AccountId, gw2ApiToken);
      }

      // compare tokens and remove
      for (let [gw2AccountId, gw2ApiToken] of Object.entries(b.gw2ApiTokens)) {
          const gw2ApiTokenA = gw2ApiTokensA.get(gw2AccountId);
          gw2ApiTokensA.delete(gw2AccountId);

          if (gw2ApiTokenA == undefined || gw2ApiToken.name != gw2ApiTokenA.name || gw2ApiToken.token != gw2ApiTokenA.token || gw2ApiToken.error != gw2ApiTokenA.error) {
              return false;
          }
      }

      // if any token is left, theyre not equal
      return gw2ApiTokensA.size < 1;
  }
}
