using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Https;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace BedMirror;

public sealed class CompanionApi : IAsyncDisposable
{
    WebApplication? app;
    public async Task Start(SunshineHost host)
    {
        if (app != null) return;
        var builder = WebApplication.CreateSlimBuilder();
        builder.Logging.ClearProviders();
        builder.WebHost.ConfigureKestrel(k => {
            k.Limits.MaxRequestBodySize = 8 * 1024 * 1024; // Allow bug reports with optional screenshot attachments
            var primary = System.Net.IPAddress.Parse(host.Address);
            foreach (var listenAddress in HostConfig.Addresses().Prepend(primary).Distinct())
            {
                k.Listen(listenAddress, HostConfig.ApiPort, listen => listen.UseHttps(new HttpsConnectionAdapterOptions {
                    ServerCertificate = host.ServerCertificate,
                    ClientCertificateMode = ClientCertificateMode.RequireCertificate,
                    ClientCertificateValidation = (cert, _, _) => host.Trusted(cert)
                }));
            }
        });
        app = builder.Build();
        app.Use(async (context, next) => {
            if (context.Connection.RemoteIpAddress is not { } ip || !HostConfig.IsLocal(ip) || !host.Trusted(await context.Connection.GetClientCertificateAsync())) { context.Response.StatusCode = 403; return; }
            context.Response.Headers.CacheControl = "no-store";
            try { await next(context); }
            catch (Exception e) { if (!context.Response.HasStarted) { context.Response.StatusCode = 400; await context.Response.WriteAsJsonAsync(new { error = e.Message }); } }
        });
        app.MapGet("/v1/status", () => new { protocolVersion = 1, running = host.Ready, privacy = host.Privacy, monitor = host.Selected, audio = AudioControl.Status(), remoteAddress = HostConfig.RemoteAddresses().FirstOrDefault()?.ToString() ?? "" });
        app.MapGet("/v1/reports", () => Results.Ok(ReportStore.GetAll()));
        app.MapGet("/v1/reports/summary", () => Results.Ok(ReportStore.GetSummary()));
        app.MapPost("/v1/reports", (ReportItem report) => {
            var saved = ReportStore.Save(report);
            return Results.Ok(new { success = true, id = saved.Id });
        });
        app.MapDelete("/v1/reports/{id}", (string id) => Results.Ok(new { success = ReportStore.Delete(id) }));
        app.MapDelete("/v1/reports", () => {
            ReportStore.ClearAll();
            return Results.Ok(new { success = true });
        });
        app.MapGet("/v1/cursor", () => CursorInfo.Read(host));
        app.MapGet("/v1/monitors", () => new { selected = host.Selected, active = host.ActiveCapture(), monitors = host.Monitors });
        app.MapPost("/v1/monitor", async (MonitorCommand command) => { await host.Switch(command.Id); return Results.Ok(new { reconnect = true }); });
        app.MapPost("/v1/monitor/live", async (MonitorCommand command) =>
        {
            var (index, ticket) = await host.PrepareLiveSwitch(command.Id);
            return Results.Ok(new { index, ticket });
        });
        app.MapPost("/v1/monitor/live/confirm", async (LiveMonitorCommand command) =>
        {
            await host.ConfirmLiveSwitch(command.Ticket);
            return Results.Ok(new { selected = host.Selected });
        });
        app.MapPost("/v1/privacy", async () => { await host.Pause(); return Results.Ok(new { privacy = true }); });
        app.MapPost("/v1/resume", async () => { await host.Resume(); return Results.Ok(new { reconnect = true }); });
        app.MapPost("/v1/media", (MediaCommand command) => { AudioControl.Command(command.Action); return Results.Ok(AudioControl.Status()); });
        app.MapPost("/v1/session", (HttpContext context,SessionCommand command) => { host.Session(context.Connection.ClientCertificate!,command.Active,command.PreventSleep); return Results.Ok(new { sleepPrevented = host.Power.Active }); });
        app.MapPost("/v1/forget", async (HttpContext context) =>
        {
            await host.Forget(context.Connection.ClientCertificate!);
            return Results.Ok(new { forgotten = true });
        });
        await app.StartAsync();
    }
    public async ValueTask DisposeAsync() { if (app != null) { await app.StopAsync(); await app.DisposeAsync(); app = null; } }
    public sealed record MonitorCommand(string Id);
    public sealed record LiveMonitorCommand(Guid Ticket);
    public sealed record MediaCommand(string Action);
    public sealed record SessionCommand(bool Active, bool PreventSleep);
}
