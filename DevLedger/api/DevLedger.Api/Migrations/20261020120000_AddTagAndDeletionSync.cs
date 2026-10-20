using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable
namespace DevLedger.Api.Migrations;

/// <summary>Additive tables; existing user, snippet, folder and financial data are retained.</summary>
public partial class AddTagAndDeletionSync : Migration
{
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.CreateTable("UserTags", columns: table => new {
            TagId = table.Column<Guid>(type: "uuid", nullable: false),
            UserId = table.Column<Guid>(type: "uuid", nullable: false),
            Name = table.Column<string>(type: "text", nullable: false),
            ColorHex = table.Column<string>(type: "text", nullable: true),
            CreatedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
        }, constraints: table => table.PrimaryKey("PK_UserTags", x => x.TagId));
        migrationBuilder.CreateIndex("IX_UserTags_UserId", "UserTags", "UserId");
        migrationBuilder.CreateTable("DeletedSyncEntities", columns: table => new {
            EntityType = table.Column<string>(type: "text", nullable: false),
            EntityId = table.Column<Guid>(type: "uuid", nullable: false),
            UserId = table.Column<Guid>(type: "uuid", nullable: false),
            DeletedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
        }, constraints: table => table.PrimaryKey("PK_DeletedSyncEntities", x => new { x.EntityType, x.EntityId }));
        migrationBuilder.CreateIndex("IX_DeletedSyncEntities_UserId", "DeletedSyncEntities", "UserId");
    }

    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable("DeletedSyncEntities");
        migrationBuilder.DropTable("UserTags");
    }
}
