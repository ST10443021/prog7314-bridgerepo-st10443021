FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS base
WORKDIR /app
EXPOSE 8080
ENV ASPNETCORE_URLS=http://+:8080

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY ["DevLedger.Api/DevLedger.Api.csproj", "DevLedger.Api/"]
RUN dotnet restore "DevLedger.Api/DevLedger.Api.csproj"
COPY . .
WORKDIR "/src/DevLedger.Api"
RUN dotnet build "DevLedger.Api.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "DevLedger.Api.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "DevLedger.Api.dll"]
