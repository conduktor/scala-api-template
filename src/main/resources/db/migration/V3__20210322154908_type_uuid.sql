/*
  Warnings:

  - The migration will change the primary key for the `post` table. If it partially fails, the table could be left without primary key constraint.
  - Changed the type of `id` on the `post` table. No cast exists, the column would be dropped and recreated, which cannot be done if there is data, since the column is required.

*/
-- AlterTable
ALTER TABLE "post" DROP CONSTRAINT "post_pkey",
DROP COLUMN "id",
ADD COLUMN     "id" UUID NOT NULL,
ADD PRIMARY KEY ("id");
