using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace DevLedger.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddDeletedSnippets : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "DeletedSnippets",
                columns: table => new
                {
                    SnippetId = table.Column<Guid>(type: "uuid", nullable: false),
                    UserId = table.Column<Guid>(type: "uuid", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DeletedSnippets", x => x.SnippetId);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DeletedSnippets_UserId",
                table: "DeletedSnippets",
                column: "UserId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "DeletedSnippets");
        }
    }
}
