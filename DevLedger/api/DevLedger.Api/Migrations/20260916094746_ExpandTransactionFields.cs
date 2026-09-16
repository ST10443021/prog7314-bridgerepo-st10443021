using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace DevLedger.Api.Migrations
{
    /// <inheritdoc />
    public partial class ExpandTransactionFields : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "AttachmentBase64",
                table: "Transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "AttachmentFileName",
                table: "Transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "ClientOrFrom",
                table: "Transactions",
                type: "text",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<bool>(
                name: "IsRecurring",
                table: "Transactions",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<DateTime>(
                name: "NextOccurrenceDate",
                table: "Transactions",
                type: "timestamp with time zone",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "PaymentMethod",
                table: "Transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "RecurringFrequency",
                table: "Transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "ReferenceNumber",
                table: "Transactions",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "RelatedProject",
                table: "Transactions",
                type: "text",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "AttachmentBase64",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "AttachmentFileName",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "ClientOrFrom",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "IsRecurring",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "NextOccurrenceDate",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "PaymentMethod",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "RecurringFrequency",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "ReferenceNumber",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "RelatedProject",
                table: "Transactions");
        }
    }
}
