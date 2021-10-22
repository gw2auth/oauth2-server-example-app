import {Component, OnInit} from '@angular/core';
import {ToastService} from '../../toast/toast.service';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {catchError, map} from 'rxjs/operators';
import {of} from 'rxjs';


@Component({
  selector: 'app-background-refresh',
  templateUrl: './background-refresh.component.html'
})
export class BackgroundRefreshComponent implements OnInit {

  isLoading = false;
  isBackgroundRefreshEnabled = false;

  constructor(private readonly toastService: ToastService, private readonly httpClient: HttpClient) { }

  ngOnInit(): void {
    this.isLoading = true;
    this.httpClient.get<boolean>('/api/background-refresh').subscribe((result) => {
      this.isBackgroundRefreshEnabled = result;
      this.isLoading = false;
    });
  }

  onBackgroundRefreshEnableClick(): void {
    this.isLoading = true;

    this.httpClient.post('/api/background-refresh', null, { observe: 'response' })
        .pipe(
            map((resp) => resp.status),
            catchError((e: HttpErrorResponse) => of(e.status))
        )
        .subscribe((r) => {
          if (r == 200) {
            this.isBackgroundRefreshEnabled = true;
            this.toastService.show('Background refresh enabled', 'The background refresh for your authorization has been enabled');
          } else {
            this.toastService.show('Failed to enable background refresh', 'The background refresh could not be enabled');
          }

          this.isLoading = false;
        });
  }

  onBackgroundRefreshDisableClick(): void {
    this.isLoading = true;

    this.httpClient.delete('/api/background-refresh', { observe: 'response' })
        .pipe(
            map((resp) => resp.status),
            catchError((e: HttpErrorResponse) => of(e.status))
        )
        .subscribe((r) => {
          if (r == 200) {
            this.isBackgroundRefreshEnabled = false;
            this.toastService.show('Background refresh disabled', 'The background refresh for your authorization has been disabled');
          } else {
            this.toastService.show('Failed to disable background refresh', 'The background refresh could not be disabled');
          }

          this.isLoading = false;
        });
  }
}
