FROM mcr.microsoft.com/dotnet/aspnet:10.0-preview AS base
WORKDIR /app
EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

FROM mcr.microsoft.com/dotnet/sdk:10.0-preview AS build
WORKDIR /src
COPY ["DevLedger/api/DevLedger.Api/DevLedger.Api.csproj", "DevLedger/api/DevLedger.Api/"]
RUN dotnet restore "DevLedger/api/DevLedger.Api/DevLedger.Api.csproj"
COPY . .
WORKDIR "/src/DevLedger/api/DevLedger.Api"
RUN dotnet build "DevLedger.Api.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "DevLedger.Api.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "DevLedger.Api.dll"]
